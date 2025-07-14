package com.boeing.bookingservice.service;

import com.boeing.bookingservice.model.entity.Booking;
import com.boeing.bookingservice.model.entity.RefundRequest;
import com.boeing.bookingservice.model.enums.BookingStatus;
import com.boeing.bookingservice.model.enums.BookingDetailStatus;
import com.boeing.bookingservice.repository.BookingRepository;
import com.boeing.bookingservice.repository.RefundRequestRepository;
import com.boeing.bookingservice.exception.ResourceNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefundRequestService {
    
    private final RefundRequestRepository refundRequestRepository;
    private final BookingRepository bookingRepository;

    /**
     * Kiểm tra user có quyền tạo refund request cho booking này không
     */
    public void verifyUserCanCreateRefundForBooking(String bookingReference, UUID userId) {
        log.info("[REFUND_SECURITY] Verifying user {} can create refund for booking: {}", userId, bookingReference);
        
        Booking booking = bookingRepository.findByBookingReference(bookingReference)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", "reference", bookingReference));
        
        if (!booking.getUserId().equals(userId)) {
            log.warn("[REFUND_SECURITY] Access denied: User {} tried to create refund for booking {} owned by {}", 
                    userId, bookingReference, booking.getUserId());
            throw new AccessDeniedException("You can only create refund requests for your own bookings.");
        }
    }
    
    /**
     * Kiểm tra user có quyền xem refund requests của booking này không
     */
    public void verifyUserCanAccessBookingRefunds(String bookingReference, UUID userId) {
        log.info("[REFUND_SECURITY] Verifying user {} can access refunds for booking: {}", userId, bookingReference);
        
        Booking booking = bookingRepository.findByBookingReference(bookingReference)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", "reference", bookingReference));
        
        if (!booking.getUserId().equals(userId)) {
            log.warn("[REFUND_SECURITY] Access denied: User {} tried to access refunds for booking {} owned by {}", 
                    userId, bookingReference, booking.getUserId());
            throw new AccessDeniedException("You can only view refund requests for your own bookings.");
        }
    }

    @Transactional
    public String createRefundRequest(String bookingReference, String reason, String requestedBy) {
        log.info("[REFUND_REQUEST] Creating refund request for booking: {}, reason: {}", bookingReference, reason);
        
        try {
            Optional<Booking> bookingOpt = bookingRepository.findByBookingReference(bookingReference);
            if (bookingOpt.isEmpty()) {
                log.error("[REFUND_REQUEST] Booking not found: {}", bookingReference);
                throw new IllegalArgumentException("Booking not found: " + bookingReference);
            }
            
            Booking booking = bookingOpt.get();
            
            // Check if refund request already exists for this booking
            List<RefundRequest> existingRequests = refundRequestRepository.findByBookingId(booking.getId());
            
            // Check for any non-rejected requests (PENDING, APPROVED, COMPLETED)
            Optional<RefundRequest> activeRequest = existingRequests.stream()
                    .filter(request -> !"REJECTED".equals(request.getStatus()))
                    .findFirst();
            
            if (activeRequest.isPresent()) {
                String existingStatus = activeRequest.get().getStatus();
                log.warn("[REFUND_REQUEST] Active refund request already exists for booking: {} with status: {}", 
                        bookingReference, existingStatus);
                
                if ("PENDING".equals(existingStatus)) {
                    throw new IllegalArgumentException("A refund request is already pending for this booking. Please wait for it to be processed.");
                } else if ("APPROVED".equals(existingStatus)) {
                    throw new IllegalArgumentException("A refund request has already been approved for this booking.");
                } else if ("COMPLETED".equals(existingStatus)) {
                    throw new IllegalArgumentException("A refund has already been completed for this booking.");
                }
            }
              // Create new refund request
            RefundRequest refundRequest = RefundRequest.builder()
                    .refundRequestId(generateRefundRequestId())
                    .bookingId(booking.getId())
                    .bookingReference(bookingReference)
                    .originalAmount(BigDecimal.valueOf(booking.getTotalAmount()))
                    .refundAmount(BigDecimal.valueOf(booking.getTotalAmount())) // Full refund by default
                    .reason(reason)
                    .status("PENDING") // PENDING -> APPROVED -> COMPLETED
                    .requestedBy(requestedBy)
                    .requestedAt(LocalDateTime.now())
                    .build();
            
            refundRequestRepository.save(refundRequest);
            
            log.info("[REFUND_REQUEST] Created refund request: {} for booking: {}, amount: {}", 
                    refundRequest.getRefundRequestId(), bookingReference, refundRequest.getRefundAmount());
            
            return refundRequest.getRefundRequestId();
            
        } catch (Exception e) {
            log.error("[REFUND_REQUEST] Error creating refund request for booking: {}", bookingReference, e);
            throw new RuntimeException("Failed to create refund request", e);
        }
    }
    
    @Transactional
    public void updateRefundRequestStatus(String refundRequestId, String status, String processedBy) {
        log.info("[REFUND_REQUEST] Updating refund request: {} to status: {}", refundRequestId, status);
        
        try {
            Optional<RefundRequest> requestOpt = refundRequestRepository.findByRefundRequestId(refundRequestId);
            if (requestOpt.isEmpty()) {
                log.error("[REFUND_REQUEST] Refund request not found: {}", refundRequestId);
                throw new IllegalArgumentException("Refund request not found: " + refundRequestId);
            }
            
            RefundRequest refundRequest = requestOpt.get();
            refundRequest.setStatus(status);
            refundRequest.setProcessedBy(processedBy);
            refundRequest.setProcessedAt(LocalDateTime.now());
            
            refundRequestRepository.save(refundRequest);
            
            // CRITICAL FIX: Update booking status when refund is completed
            if ("COMPLETED".equals(status)) {
                updateBookingStatusAfterRefundCompletion(refundRequest.getBookingReference());
            }
            
            log.info("[REFUND_REQUEST] Updated refund request: {} to status: {}", refundRequestId, status);
            
        } catch (Exception e) {
            log.error("[REFUND_REQUEST] Error updating refund request: {}", refundRequestId, e);
            throw new RuntimeException("Failed to update refund request", e);
        }
    }
    
    /**
     * Updates booking status to CANCELLED when refund is completed
     * Only updates bookings that were previously PAID (actual refunds)
     */
    private void updateBookingStatusAfterRefundCompletion(String bookingReference) {
        try {
            log.info("[REFUND_COMPLETION] Updating booking status for completed refund: {}", bookingReference);
            
            Optional<Booking> bookingOpt = bookingRepository.findByBookingReference(bookingReference);
            if (bookingOpt.isEmpty()) {
                log.error("[REFUND_COMPLETION] Booking not found: {}", bookingReference);
                return;
            }
            
            Booking booking = bookingOpt.get();
            
            // CRITICAL FIX: Only update status if booking was previously PAID
            // Don't change CANCELLED_NO_PAYMENT bookings (no payment = no refund)
            if (booking.getStatus() == BookingStatus.PAID) {
                log.info("[REFUND_COMPLETION] Updating PAID booking {} to CANCELLED after refund completion", bookingReference);
                
                // Update booking status to CANCELLED since refund is completed
                booking.setStatus(BookingStatus.CANCELLED);
                booking.setUpdatedAt(LocalDateTime.now());
                
                // Update all booking details to CANCELLED status
                if (booking.getBookingDetails() != null) {
                    booking.getBookingDetails().forEach(detail -> {
                        detail.setStatus(BookingDetailStatus.CANCELLED);
                    });
                }
                
                bookingRepository.save(booking);
                
                log.info("[REFUND_COMPLETION] Booking {} status updated from PAID to CANCELLED after refund completion", bookingReference);
            } else {
                log.warn("[REFUND_COMPLETION] Booking {} has status {} - not updating to CANCELLED as it was not PAID", 
                        bookingReference, booking.getStatus());
            }
            
        } catch (Exception e) {
            log.error("[REFUND_COMPLETION] Error updating booking status after refund completion for booking: {}", bookingReference, e);
            // Don't throw - refund completion shouldn't fail due to status update issues
        }
    }
    
    private String generateRefundRequestId() {
        // Generate refund request ID: RR-YYYYMMDD-XXXXXX
        String datePrefix = LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        String randomSuffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        return "RR-" + datePrefix + "-" + randomSuffix;
    }
}
