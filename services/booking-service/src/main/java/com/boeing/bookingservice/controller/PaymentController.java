package com.boeing.bookingservice.controller;

import com.boeing.bookingservice.dto.request.CreatePaymentRequest;
import com.boeing.bookingservice.dto.response.PaymentStatusDTO;
import com.boeing.bookingservice.exception.PaymentProcessingException;
import com.boeing.bookingservice.security.AuthenticatedUserPrincipal;
import com.boeing.bookingservice.service.impl.PaymentServiceImpl;
import com.boeing.bookingservice.event.PaymentProcessedEvent;
import com.boeing.bookingservice.saga.event.PaymentCompletedForBookingEvent;
import com.boeing.bookingservice.model.entity.Booking;
import com.boeing.bookingservice.repository.BookingRepository;
import com.boeing.bookingservice.saga.state.SagaState;
import com.boeing.bookingservice.saga.state.SagaStateRepository;
import com.boeing.bookingservice.saga.SagaStep;
import org.springframework.context.ApplicationEventPublisher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payment")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentServiceImpl paymentService;
    private final ApplicationEventPublisher eventPublisher;
    private final BookingRepository bookingRepository;
    private final SagaStateRepository sagaStateRepository;

    @PostMapping("/vnpay/create-url")
    @PreAuthorize("hasAuthority('USER')")
    public ResponseEntity<?> createPaymentUrl(@RequestBody CreatePaymentRequest request,
                                              HttpServletRequest httpRequest,
                                              Authentication authentication) {
        try {
            log.info("[PAYMENT_CONTROLLER] Creating payment URL for booking: {}, payment method: {}", 
                    request.getBookingReference(), request.getPaymentMethod());
                    
            // Default to VNPAY_BANKTRANSFER if payment method is missing
            if (request.getPaymentMethod() == null || request.getPaymentMethod().trim().isEmpty()) {
                log.warn("[PAYMENT_CONTROLLER] Payment method is null or empty, defaulting to VNPAY_BANKTRANSFER");
                request.setPaymentMethod("VNPAY_BANKTRANSFER");
            }
            
            UUID userId = getUserIdFromAuthentication(authentication);
            
            String paymentUrl = paymentService.createVNPayPaymentUrl(request, userId);
            
            if (paymentUrl == null || paymentUrl.trim().isEmpty()) {
                log.error("[PAYMENT_CONTROLLER] Failed to generate payment URL for booking: {}", request.getBookingReference());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to generate payment URL"));
            }
            
            log.info("[PAYMENT_CONTROLLER] Successfully created payment URL for booking: {}", request.getBookingReference());
            return ResponseEntity.ok(Map.of("paymentUrl", paymentUrl));
        } catch (Exception e) {
            log.error("[PAYMENT_CONTROLLER] Error creating payment URL: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/vn-pay-callback")
    public void handleCallback(HttpServletRequest request, HttpServletResponse response) throws IOException {
        paymentService.processVNPayCallback(request, response);
    }

    @GetMapping("/status/{bookingReference}")
    @PreAuthorize("hasAnyAuthority('USER', 'ADMIN')")
    public ResponseEntity<PaymentStatusDTO> getPaymentStatus(@PathVariable String bookingReference,
                                                            Authentication authentication) {
        try {
            UUID userId = getUserIdFromAuthentication(authentication);
            
            PaymentStatusDTO status = paymentService.getPaymentStatus(bookingReference, userId);
            return ResponseEntity.ok(status);
        } catch (PaymentProcessingException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }



    @PostMapping("/verify-and-complete")
    @PreAuthorize("hasAnyAuthority('USER', 'ADMIN')")
    public ResponseEntity<?> verifyPaymentAndComplete(@RequestParam String bookingReference,
                                                     Authentication authentication) {
        try {
            UUID userId = getUserIdFromAuthentication(authentication);
            
            log.info("[PAYMENT_VERIFY] User {} requesting payment verification for booking: {}", userId, bookingReference);
            
            // Get current payment status
            PaymentStatusDTO status = paymentService.getPaymentStatus(bookingReference, userId);
            
            // If payment is completed but saga might be stuck, trigger progression
            if ("COMPLETED".equals(status.getPaymentStatus())) {
                log.info("[PAYMENT_VERIFY] Payment already completed, triggering saga progression for booking: {}", bookingReference);
                eventPublisher.publishEvent(new PaymentProcessedEvent(this, bookingReference, true, "VERIFICATION_TRIGGER"));
                
                return ResponseEntity.ok(Map.of(
                    "message", "Payment verified and saga progression triggered",
                    "bookingReference", bookingReference,
                    "paymentStatus", status.getPaymentStatus()
                ));
            } else {
                return ResponseEntity.ok(Map.of(
                    "message", "Payment not yet completed",
                    "bookingReference", bookingReference,
                    "paymentStatus", status.getPaymentStatus()
                ));
            }
            
        } catch (PaymentProcessingException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[PAYMENT_VERIFY] Error verifying payment for booking: {}", bookingReference, e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }



    private UUID getUserIdFromAuthentication(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("User is not authenticated.");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof AuthenticatedUserPrincipal) {
            return ((AuthenticatedUserPrincipal) principal).getUserIdAsUUID();
        }
        throw new AccessDeniedException("Cannot determine user ID from authentication principal.");
    }
}