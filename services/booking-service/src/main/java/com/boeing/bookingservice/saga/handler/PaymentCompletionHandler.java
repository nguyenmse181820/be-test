package com.boeing.bookingservice.saga.handler;

import com.boeing.bookingservice.integration.fs.FlightClient;
import com.boeing.bookingservice.integration.fs.dto.FsConfirmSeatsRequestDTO;
import com.boeing.bookingservice.integration.fs.dto.FsConfirmSeatsResponseDTO;
import com.boeing.bookingservice.integration.fs.dto.FsReleaseSeatsRequestDTO;
import com.boeing.bookingservice.integration.fs.dto.FsReleaseSeatsResponseDTO;
import com.boeing.bookingservice.model.entity.Booking;
import com.boeing.bookingservice.model.entity.BookingDetail;
import com.boeing.bookingservice.model.enums.BookingStatus;
import com.boeing.bookingservice.repository.BookingRepository;
import com.boeing.bookingservice.saga.command.ConfirmSeatsForBookingCommand;
import com.boeing.bookingservice.saga.command.UseVoucherCommand;
import com.boeing.bookingservice.saga.command.CancelVoucherUsageCommand;
import com.boeing.bookingservice.saga.event.PaymentCompletedForBookingEvent;
import com.boeing.bookingservice.saga.event.SeatsConfirmationResponseEvent;
import com.boeing.bookingservice.saga.event.VoucherUsedEvent;
import com.boeing.bookingservice.saga.orchestrator.CreateBookingSagaOrchestrator;
import com.boeing.bookingservice.saga.state.SagaState;
import com.boeing.bookingservice.saga.state.SagaStateRepository;
import com.boeing.bookingservice.saga.SagaStep;
import com.boeing.bookingservice.service.RefundRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentCompletionHandler {

    private final SagaStateRepository sagaStateRepository;
    private final CreateBookingSagaOrchestrator sagaOrchestrator;
    private final BookingRepository bookingRepository;
    private final FlightClient flightClient;
    private final RabbitTemplate rabbitTemplate;
    private final RefundRequestService refundRequestService;

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${app.rabbitmq.exchanges.events}")
    private String eventsExchange;

    @Value("${app.rabbitmq.routingKeys.seatConfirmationResponse}")
    private String RK_SEAT_CONFIRMATION_RESPONSE;    
    
    @EventListener
    @Transactional(propagation = Propagation.REQUIRED, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void handlePaymentCompleted(PaymentCompletedForBookingEvent event) {
        UUID sagaId = event.getSagaId();
        
        try {
            // Try to find saga state with enhanced retry logic for transaction consistency
            SagaState sagaState = findSagaStateWithRetry(sagaId, 5, 200);
            
            if (sagaState == null) {
                log.error("Saga state not found for sagaId: {} after retries. Cannot proceed with payment completion.", sagaId);
                throw new IllegalStateException("Saga state not found for sagaId: " + sagaId + ". Payment completion cannot proceed without saga state.");
            }
            
            // Use the unified updateSagaState method to update to PAYMENT_COMPLETED
            updateSagaState(sagaId, SagaStep.PAYMENT_COMPLETED);

            if (event.isPaymentSuccessful()) {
                triggerSeatConfirmation(event);
            } else {
                log.warn("Payment failed for saga {}, booking: {}", sagaId, event.getBookingReference());
                handlePaymentFailure(event);
            }
            
        } catch (Exception e) {
            log.error("Error processing payment completion for saga: {}", sagaId, e);
            handleSeatConfirmationFailure(sagaId, event.getBookingReference(), "INTERNAL_ERROR", 
                    "Error in payment completion handler: " + e.getMessage());
        }
    }

    private void triggerSeatConfirmation(PaymentCompletedForBookingEvent event) {
        UUID sagaId = event.getSagaId();
        String bookingReference = event.getBookingReference();
        
        try {
            // Get booking details to extract seat information
            Optional<Booking> bookingOpt = bookingRepository.findByBookingReference(bookingReference);
            if (bookingOpt.isEmpty()) {
                log.error("Booking not found for reference: {}", bookingReference);
                handleSeatConfirmationFailure(sagaId, bookingReference, "BOOKING_NOT_FOUND", 
                        "Booking not found in database");
                return;
            }

            Booking booking = bookingOpt.get();
            
            // Extract flight ID and seat codes from booking details
            if (booking.getBookingDetails() == null || booking.getBookingDetails().isEmpty()) {
                log.error("No booking details found for booking: {}", bookingReference);
                handleSeatConfirmationFailure(sagaId, bookingReference, "NO_BOOKING_DETAILS", 
                        "No booking details found");
                return;
            }

            // Group booking details by flight to handle multi-flight bookings
            Map<UUID, List<BookingDetail>> flightToDetails = booking.getBookingDetails()
                    .stream()
                    .collect(java.util.stream.Collectors.groupingBy(BookingDetail::getFlightId));
            
            // Update saga state to AWAITING_SEAT_CONFIRMATION
            updateSagaState(sagaId, SagaStep.AWAITING_SEAT_CONFIRMATION);
            
            boolean allFlightsConfirmed = true;
            List<String> allConfirmedSeats = new ArrayList<>();
            List<String> allFailedSeats = new ArrayList<>();
            
            // Process each flight separately
            for (Map.Entry<UUID, List<BookingDetail>> entry : flightToDetails.entrySet()) {
                UUID flightId = entry.getKey();
                List<BookingDetail> flightDetails = entry.getValue();
                
                // Extract seat codes for this specific flight
                List<String> flightSeatCodes = flightDetails.stream()
                        .map(BookingDetail::getSelectedSeatCode)
                        .filter(seat -> seat != null && !seat.trim().isEmpty())
                        .collect(java.util.stream.Collectors.toList());
                
                if (flightSeatCodes.isEmpty()) {
                    // Generate dummy seats if none selected
                    for (int i = 1; i <= flightDetails.size(); i++) {
                        flightSeatCodes.add(i + "A");
                    }
                }
                
                // Call Flight Service to confirm seats for this flight
                FsConfirmSeatsRequestDTO request = FsConfirmSeatsRequestDTO.builder()
                        .bookingReference(bookingReference)
                        .seatCodes(flightSeatCodes)
                        .build();

                try {
                    FsConfirmSeatsResponseDTO response = flightClient.confirmSeats(flightId, request);
                    
                    if (response == null) {
                        log.error("Flight service returned null response for flight: {}, booking: {}", 
                                flightId, bookingReference);
                        allFlightsConfirmed = false;
                        allFailedSeats.addAll(flightSeatCodes);
                        continue;
                    }
                    
                    // Check if this flight's seats were confirmed
                    boolean flightConfirmed = "Success".equalsIgnoreCase(response.getStatus()) &&
                            (response.getFailedToConfirmSeats() == null || response.getFailedToConfirmSeats().isEmpty());
                    
                    if (flightConfirmed) {
                        allConfirmedSeats.addAll(response.getConfirmedSeats() != null ? response.getConfirmedSeats() : flightSeatCodes);
                    } else {
                        allFlightsConfirmed = false;
                        allFailedSeats.addAll(response.getFailedToConfirmSeats() != null ? response.getFailedToConfirmSeats() : flightSeatCodes);
                    }
                    
                } catch (feign.FeignException e) {
                    log.error("Feign error calling Flight Service for flight: {}, booking: {} - Status: {}, Body: {}", 
                            flightId, bookingReference, e.status(), e.contentUTF8(), e);
                    allFlightsConfirmed = false;
                    allFailedSeats.addAll(flightSeatCodes);
                } catch (Exception e) {
                    log.error("Unexpected error during seat confirmation for flight: {}, booking: {}", 
                            flightId, bookingReference, e);
                    allFlightsConfirmed = false;
                    allFailedSeats.addAll(flightSeatCodes);
                }
            }
            
            // Process overall result
            if (allFlightsConfirmed) {
                handleSeatConfirmationSuccess(sagaId, bookingReference, allConfirmedSeats);
            } else {
                log.error("Seat confirmation failed for booking: {}, failed seats: {}", 
                        bookingReference, allFailedSeats);
                handleSeatConfirmationFailure(sagaId, bookingReference, "SEATS_NOT_AVAILABLE", 
                        "Failed to confirm seats for some flights. Failed seats: " + allFailedSeats);
            }
        } catch (Exception e) {
            log.error("Error in seat confirmation setup for saga: {}, booking: {}", 
                    sagaId, bookingReference, e);
            handleSeatConfirmationFailure(sagaId, bookingReference, "SETUP_ERROR", 
                    "Error setting up seat confirmation: " + e.getMessage());
        }
    }

    private void handleSeatConfirmationResponse(UUID sagaId, String bookingReference, 
                                              FsConfirmSeatsResponseDTO response, List<String> requestedSeats) {
        
        boolean allSeatsConfirmed = response != null && 
                "Success".equalsIgnoreCase(response.getStatus()) &&
                (response.getFailedToConfirmSeats() == null || response.getFailedToConfirmSeats().isEmpty());

        if (allSeatsConfirmed) {
            // Update saga state to SEATS_CONFIRMED
            updateSagaState(sagaId, SagaStep.SEATS_CONFIRMED);
            
            // Update booking status to PAID
            try {
                updateBookingStatusToPaid(bookingReference);
            } catch (Exception e) {
                log.error("Failed to update booking status for booking: {}", bookingReference, e);
            }
            
            // Trigger loyalty points earning
            try {
                triggerLoyaltyPointsEarning(bookingReference);
            } catch (Exception e) {
                log.error("Failed to trigger loyalty points earning for booking: {}", bookingReference, e);
            }
            
            // Publish success event for further processing (notifications, etc.)
            publishSeatsConfirmationEvent(sagaId, bookingReference, true, 
                    response.getConfirmedSeats(), null, null, "All seats confirmed successfully");
            
            // Send booking confirmation notification
            try {
                sendBookingConfirmationNotification(bookingReference);
            } catch (Exception e) {
                log.error("Failed to send confirmation notification for booking: {}", bookingReference, e);
            }
            
            // Complete the saga successfully
            completeSagaSuccessfully(sagaId, bookingReference);
            
        } else {
            String failureReason = response != null ? response.getMessage() : "Unknown error";
            List<String> failedSeats = response != null ? response.getFailedToConfirmSeats() : requestedSeats;
            
            log.warn("Seat confirmation failed for booking: {}, failed seats: {}, reason: {}", 
                    bookingReference, failedSeats, failureReason);
            
            // Publish failure event
            publishSeatsConfirmationEvent(sagaId, bookingReference, false, 
                    response != null ? response.getConfirmedSeats() : null, 
                    failedSeats, failureReason, "Seat confirmation failed");
            
            // Handle the failure (initiate compensation)
            handleSeatConfirmationFailure(sagaId, bookingReference, "SEATS_NOT_AVAILABLE", failureReason);
        }
    }    
    
    private void handleSeatConfirmationFailure(UUID sagaId, String bookingReference, 
                                             String errorCode, String errorMessage) {
        try {
            
            // Update saga state to failure
            updateSagaState(sagaId, SagaStep.FAILED_SEAT_CONFIRMATION);
            
            // Implement compensation logic:
            
            // 1. Cancel voucher usage if voucher was applied (restore voucher to unused state)
            try {
                cancelVoucherUsageForBooking(sagaId, bookingReference, "SEAT_CONFIRMATION_FAILED: " + errorMessage);
            } catch (Exception e) {
                log.error("Failed to cancel voucher usage for booking: {}", bookingReference, e);
            }
            
            // 2. Create refund request (manual process for staff)
            try {
                String refundRequestId = refundRequestService.createRefundRequest(
                    bookingReference, 
                    "SEAT_CONFIRMATION_FAILED: " + errorMessage,
                    "SYSTEM"
                );
            } catch (Exception e) {
                log.error("Failed to create refund request for booking: {}", 
                        bookingReference, e);
            }
            
            // 3. Release fare inventory (call flight service)
            try {
                releaseFareInventory(bookingReference, "SEAT_CONFIRMATION_FAILED");
            } catch (Exception e) {
                log.error("Failed to release fare inventory for booking: {}", 
                        bookingReference, e);
            }
            
            // 4. Update booking status to FAILED
            try {
                updateBookingStatusToFailed(bookingReference, errorMessage);
            } catch (Exception e) {
                log.error("Failed to update booking status for booking: {}", 
                        bookingReference, e);
            }
            
        } catch (Exception e) {
            log.error("Error handling seat confirmation failure for saga: {}", sagaId, e);
        }
    }    private void handlePaymentFailure(PaymentCompletedForBookingEvent event) {
        UUID sagaId = event.getSagaId();
        String bookingReference = event.getBookingReference();
        
        try {
            // Update saga state to payment failed
            updateSagaState(sagaId, SagaStep.FAILED_PAYMENT);
            
            // Implement compensation logic for payment failure:
            
            // 1. Cancel voucher usage if voucher was applied (restore voucher to unused state)
            try {
                cancelVoucherUsageForBooking(sagaId, bookingReference, "PAYMENT_FAILED: " + event.getTransactionId());
            } catch (Exception e) {
                log.error("Failed to cancel voucher usage for booking: {}", bookingReference, e);
            }

            // 2. Release fare inventory (since payment failed, release the held fares)
            try {
                releaseFareInventory(bookingReference, "PAYMENT_FAILED: " + event.getTransactionId());
            } catch (Exception e) {
                log.error("Failed to release fare inventory for booking: {}", bookingReference, e);
            }
            
            // 3. Release any held seats (if user had pre-selected seats)
            try {
                releaseSeatsFromBooking(bookingReference, "PAYMENT_FAILED: " + event.getTransactionId());
            } catch (Exception e) {
                log.error("Failed to release seats for booking: {}", bookingReference, e);
            }
            
            // 4. Update booking status to CANCELLED
            try {
                updateBookingStatusToCancelled(bookingReference, "Payment failed: " + event.getTransactionId());
            } catch (Exception e) {
                log.error("Failed to update booking status for booking: {}", bookingReference, e);
            }
            
            // 5. Handle partial payment refund (if any payment was made but failed to complete)
            // This would be rare but possible in some payment scenarios
            try {
                handlePartialPaymentRefund(bookingReference, event.getTransactionId());
            } catch (Exception e) {
                log.error("Failed to handle partial payment refund for booking: {}", bookingReference, e);
            }
            
            // 6. Send notification to customer about payment failure
            try {
                sendPaymentFailureNotification(bookingReference, event.getTransactionId());
            } catch (Exception e) {
                log.error("Failed to send payment failure notification for booking: {}", bookingReference, e);
            }
            
        } catch (Exception e) {
            log.error("Error handling payment failure for saga: {}, booking: {}", 
                    sagaId, bookingReference, e);
        }
    }

    private void completeSagaSuccessfully(UUID sagaId, String bookingReference) {
        try {
            // Update saga state to completed
            updateSagaState(sagaId, SagaStep.COMPLETED_SUCCESSFULLY);
            
        } catch (Exception e) {
            log.error("Error completing saga for saga: {}", sagaId, e);
        }
    }

    private void updateSagaState(UUID sagaId, SagaStep newStep) {
        try {
            // Use enhanced retry logic for finding saga state to handle transaction consistency
            SagaState sagaState = findSagaStateWithRetry(sagaId, 5, 100);
            
            if (sagaState != null) {
                SagaStep oldStep = sagaState.getCurrentStep();
                sagaState.setCurrentStep(newStep);
                sagaState.setUpdatedAt(LocalDateTime.now());
                sagaStateRepository.save(sagaState);
            } else {
                // As a last resort, create the saga state if not found
                // This should not normally happen but helps with transaction timing issues
                log.warn("Saga state not found for sagaId: {}, creating emergency saga state for step: {}", sagaId, newStep);
                createEmergencySagaState(sagaId, newStep);
            }
        } catch (Exception e) {
            log.error("Error updating saga state for saga: {}", sagaId, e);
            throw e;
        }
    }

    /**
     * Creates an emergency saga state when the original state cannot be found
     * This is a fallback to prevent payment completion from failing due to saga state issues
     */
    private void createEmergencySagaState(UUID sagaId, SagaStep step) {
        try {
            log.warn("Creating emergency saga state for sagaId: {} with step: {}", sagaId, step);
            
            SagaState emergencySagaState = SagaState.builder()
                    .sagaId(sagaId)
                    .currentStep(step)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .payloadJson("{\"emergency\":true}") // Minimal payload
                    .errorMessage("Emergency saga state created during payment completion")
                    .build();
            
            sagaStateRepository.save(emergencySagaState);
            
        } catch (Exception e) {
            log.error("Failed to create emergency saga state for sagaId: {}", sagaId, e);
            throw new IllegalStateException("Cannot create emergency saga state for sagaId: " + sagaId + ". Payment completion failed.", e);
        }
    }

    private void publishSeatsConfirmationEvent(UUID sagaId, String bookingReference, boolean success,
                                             List<String> confirmedSeats, List<String> failedSeats,
                                             String failureReason, String message) {
        try {
            SeatsConfirmationResponseEvent event = SeatsConfirmationResponseEvent.builder()
                    .sagaId(sagaId)
                    .bookingReference(bookingReference)
                    .success(success)
                    .confirmedSeats(confirmedSeats)
                    .failedSeats(failedSeats)
                    .failureReason(failureReason)
                    .message(message)
                    .build();

            rabbitTemplate.convertAndSend(eventsExchange, RK_SEAT_CONFIRMATION_RESPONSE, event);
            
        } catch (Exception e) {
            log.error("Error publishing seat confirmation event for booking: {}", 
                    bookingReference, e);
        }
    }

    private List<String> getSeatCodesFromBookingDetails(Booking booking) {
        List<String> seatCodes = new ArrayList<>();
        boolean hasSeatSelections = false;
        
        for (BookingDetail detail : booking.getBookingDetails()) {
            if (detail.getSelectedSeatCode() != null && !detail.getSelectedSeatCode().trim().isEmpty()) {
                seatCodes.add(detail.getSelectedSeatCode());
                hasSeatSelections = true;
            }
        }
        
        if (!hasSeatSelections) {
            // Fallback: generate dummy seat codes if no seats were selected
            return generateSeatCodesForBooking(booking);
        }
        
        return seatCodes;
    }

    private void releaseSeatsFromBooking(String bookingReference, String reason) {
        try {
            Optional<Booking> bookingOpt = bookingRepository.findByBookingReference(bookingReference);
            if (bookingOpt.isEmpty()) {
                log.warn("Booking not found for seat release: {}", bookingReference);
                return;
            }
            
            Booking booking = bookingOpt.get();
            List<String> seatCodes = getSeatCodesFromBookingDetails(booking);
            
            // Only release if there are actual pre-selected seats (not dummy generated ones)
            boolean hasRealSeatSelections = booking.getBookingDetails().stream()
                    .anyMatch(detail -> detail.getSelectedSeatCode() != null && 
                             !detail.getSelectedSeatCode().trim().isEmpty());
            
            if (!hasRealSeatSelections) {
                return;
            }
            
            if (!seatCodes.isEmpty()) {
                BookingDetail firstDetail = booking.getBookingDetails().get(0);
                UUID flightId = firstDetail.getFlightId();
                
                FsReleaseSeatsRequestDTO request = FsReleaseSeatsRequestDTO.builder()
                        .bookingReference(bookingReference)
                        .seatCodes(seatCodes)
                        .reason(reason)
                        .build();

                FsReleaseSeatsResponseDTO response = flightClient.releaseSeats(flightId, request);
                
                if (response == null || !"Success".equalsIgnoreCase(response.getStatus())) {
                    log.warn("Failed to release some seats for booking: {}, failed seats: {}", 
                            bookingReference, response != null ? response.getFailedToReleaseSeats() : "unknown");
                }
            }
        } catch (Exception e) {
            log.error("Error releasing seats for booking: {}", bookingReference, e);
            throw e;
        }
    }

    private List<String> generateSeatCodesForBooking(Booking booking) {
        int passengerCount = booking.getBookingDetails().size();
        List<String> seatCodes = new java.util.ArrayList<>();
        
        // Generate dummy seat codes (in production, these would be actual selected seats)
        for (int i = 1; i <= passengerCount; i++) {
            seatCodes.add(i + "A"); // Simple seat code generation in "1A" format
        }
        
        return seatCodes;
    }
    
    private void releaseFareInventory(String bookingReference, String reason) {
        try {
            Optional<Booking> bookingOpt = bookingRepository.findByBookingReference(bookingReference);
            if (bookingOpt.isEmpty()) {
                log.warn("Booking not found for fare release: {}", bookingReference);
                return;
            }
            
            Booking booking = bookingOpt.get();
            if (booking.getBookingDetails() != null && !booking.getBookingDetails().isEmpty()) {
                BookingDetail firstDetail = booking.getBookingDetails().get(0);
                UUID flightId = firstDetail.getFlightId();
                String fareName = firstDetail.getSelectedFareName();
                
                // Call flight service to release fare (reuse existing DTO from BookingCompensationHandler)
                com.boeing.bookingservice.integration.fs.dto.FsReleaseFareRequestDTO request = 
                        com.boeing.bookingservice.integration.fs.dto.FsReleaseFareRequestDTO.builder()
                                .bookingReference(bookingReference)
                                .fareName(fareName)
                                .countToRelease(booking.getPassengerCount())
                                .reason(reason)
                                .build();

                com.boeing.bookingservice.integration.fs.dto.FsReleaseFareResponseDTO response = 
                        flightClient.releaseFare(flightId, fareName, request);

                if (response == null || !response.isSuccess()) {
                    log.warn("Failed to release fare inventory for booking: {}", bookingReference);
                }
            }
        } catch (Exception e) {
            log.error("Error releasing fare inventory for booking: {}", bookingReference, e);
            throw e;
        }
    }
    
    private void updateBookingStatusToFailed(String bookingReference, String failureReason) {
        try {
            Optional<Booking> bookingOpt = bookingRepository.findByBookingReference(bookingReference);
            if (bookingOpt.isEmpty()) {
                log.warn("Booking not found for status update: {}", bookingReference);
                return;
            }
            
            Booking booking = bookingOpt.get();
            booking.setStatus(BookingStatus.FAILED_TO_CONFIRM_SEATS);
            booking.setUpdatedAt(java.time.LocalDateTime.now());
            // Add failure reason to notes or create a separate failure reason field
            
            bookingRepository.save(booking);
            
        } catch (Exception e) {
            log.error("Error updating booking status for booking: {}", bookingReference, e);
            throw e;
        }
    }
    
    private void updateBookingStatusToCancelled(String bookingReference, String cancellationReason) {
        try {
            Optional<Booking> bookingOpt = bookingRepository.findByBookingReference(bookingReference);
            if (bookingOpt.isEmpty()) {
                log.warn("Booking not found for status update: {}", bookingReference);
                return;
            }
            
            Booking booking = bookingOpt.get();
            booking.setStatus(BookingStatus.CANCELLED);
            booking.setUpdatedAt(java.time.LocalDateTime.now());
            // You might want to add a cancellation_reason field to booking table
            
            bookingRepository.save(booking);
            
        } catch (Exception e) {
            log.error("Error updating booking status for booking: {}", bookingReference, e);
            throw e;
        }
    }
    
    private void updateBookingStatusToPaid(String bookingReference) {
        try {
            Optional<Booking> bookingOpt = bookingRepository.findByBookingReference(bookingReference);
            if (bookingOpt.isEmpty()) {
                log.warn("Booking not found for status update: {}", bookingReference);
                return;
            }
            
            Booking booking = bookingOpt.get();
            booking.setStatus(BookingStatus.PAID);
            booking.setUpdatedAt(java.time.LocalDateTime.now());
            
            bookingRepository.save(booking);
            
        } catch (Exception e) {
            log.error("Error updating booking status for booking: {}", bookingReference, e);
            throw e;
        }
    }
    
    private void triggerVoucherUsage(UUID sagaId, String bookingReference) {
        try {
            Optional<Booking> bookingOpt = bookingRepository.findByBookingReference(bookingReference);
            if (bookingOpt.isEmpty()) {
                log.warn("Booking not found for voucher usage: {}", bookingReference);
                return;
            }
            
            Booking booking = bookingOpt.get();
            
            // Check if a voucher was applied to this booking
            if (booking.getAppliedVoucherCode() != null && !booking.getAppliedVoucherCode().trim().isEmpty()) {
                log.info("Triggering voucher usage for booking: {}, voucher: {}", 
                        bookingReference, booking.getAppliedVoucherCode());
                
                UseVoucherCommand command = UseVoucherCommand.builder()
                        .sagaId(sagaId)
                        .voucherCode(booking.getAppliedVoucherCode())
                        .userId(booking.getUserId())
                        .build();
                
                // Update saga state to indicate voucher usage is being processed
                updateSagaState(sagaId, SagaStep.AWAITING_LS_VOUCHER_USE_RESPONSE);
                
                // Send command to mark voucher as used
                rabbitTemplate.convertAndSend("voucher.use.exchange", "", command);
                
                log.info("Voucher usage command sent for booking: {}, voucher: {}", 
                        bookingReference, booking.getAppliedVoucherCode());
            } else {
                log.debug("No voucher applied to booking: {}, skipping voucher usage", bookingReference);
            }
            
        } catch (Exception e) {
            log.error("Error triggering voucher usage for booking: {}", bookingReference, e);
            // Don't throw - voucher usage failure shouldn't fail the booking completion
        }
    }

    private void cancelVoucherUsageForBooking(UUID sagaId, String bookingReference, String reason) {
        try {
            Optional<Booking> bookingOpt = bookingRepository.findByBookingReference(bookingReference);
            if (bookingOpt.isEmpty()) {
                log.warn("Booking not found for voucher cancellation: {}", bookingReference);
                return;
            }
            
            Booking booking = bookingOpt.get();
            
            // Check if a voucher was applied to this booking
            if (booking.getAppliedVoucherCode() != null && !booking.getAppliedVoucherCode().trim().isEmpty()) {
                log.info("Cancelling voucher usage for booking: {}, voucher: {}, reason: {}", 
                        bookingReference, booking.getAppliedVoucherCode(), reason);
                
                CancelVoucherUsageCommand command = CancelVoucherUsageCommand.builder()
                        .sagaId(sagaId)
                        .voucherCode(booking.getAppliedVoucherCode())
                        .userId(booking.getUserId())
                        .reason(reason)
                        .build();
                
                // Send command to cancel voucher usage (restore voucher to unused state)
                rabbitTemplate.convertAndSend("voucher.cancel.exchange", "", command);
                
                log.info("Voucher usage cancellation command sent for booking: {}, voucher: {}", 
                        bookingReference, booking.getAppliedVoucherCode());
            } else {
                log.debug("No voucher applied to booking: {}, skipping voucher cancellation", bookingReference);
            }
            
        } catch (Exception e) {
            log.error("Error cancelling voucher usage for booking: {}", bookingReference, e);
            // Don't throw - voucher cancellation failure shouldn't stop other compensation actions
        }
    }

    private void triggerLoyaltyPointsEarning(String bookingReference) {
        try {
            Optional<Booking> bookingOpt = bookingRepository.findByBookingReference(bookingReference);
            if (bookingOpt.isEmpty()) {
                log.warn("Booking not found for loyalty points: {}", bookingReference);
                return;
            }
            
            Booking booking = bookingOpt.get();
            
            // Calculate points based on booking total amount
            double totalAmount = booking.getTotalAmount();
            int pointsToEarn = (int) Math.floor(totalAmount / 100); // 1 point per 100 VND
            
            // TODO: Call Loyalty Service to add points
            // LoyaltyPointsEarnCommand command = LoyaltyPointsEarnCommand.builder()
            //     .userId(booking.getUserId())
            //     .bookingReference(bookingReference)
            //     .pointsToEarn(pointsToEarn)
            //     .transactionType("BOOKING_COMPLETED")
            //     .build();
            // 
            // rabbitTemplate.convertAndSend(loyaltyExchange, loyaltyPointsEarnRoutingKey, command);
            
        } catch (Exception e) {
            log.error("Error triggering loyalty points for booking: {}", bookingReference, e);
            // Don't throw - loyalty points earning is not critical for booking completion
        }
    }
    
    private void sendBookingConfirmationNotification(String bookingReference) {
        try {
            Optional<Booking> bookingOpt = bookingRepository.findByBookingReference(bookingReference);
            if (bookingOpt.isEmpty()) {
                log.warn("Booking not found for notification: {}", bookingReference);
                return;
            }
            
            Booking booking = bookingOpt.get();
            
            // TODO: Call Notification Service
            // BookingConfirmationNotificationCommand command = BookingConfirmationNotificationCommand.builder()
            //     .userId(booking.getUserId())
            //     .bookingReference(bookingReference)
            //     .customerEmail(booking.getContactEmail())
            //     .customerPhone(booking.getContactPhone())
            //     .build();
            // 
            // rabbitTemplate.convertAndSend(notificationExchange, bookingConfirmationRoutingKey, command);
            
        } catch (Exception e) {
            log.error("Error sending booking confirmation notification for booking: {}", bookingReference, e);
            // Don't throw - notification failure shouldn't fail the booking
        }
    }

    private void sendPaymentFailureNotification(String bookingReference, String transactionId) {
        try {
            Optional<Booking> bookingOpt = bookingRepository.findByBookingReference(bookingReference);
            if (bookingOpt.isEmpty()) {
                log.warn("Booking not found for payment failure notification: {}", bookingReference);
                return;
            }
            
            Booking booking = bookingOpt.get();
            
            // TODO: Call Notification Service
            // PaymentFailureNotificationCommand command = PaymentFailureNotificationCommand.builder()
            //     .userId(booking.getUserId())
            //     .bookingReference(bookingReference)
            //     .transactionId(transactionId)
            //     .customerEmail(booking.getContactEmail())
            //     .customerPhone(booking.getContactPhone())
            //     .build();
            // 
            // rabbitTemplate.convertAndSend(notificationExchange, paymentFailureRoutingKey, command);
            
        } catch (Exception e) {
            log.error("Error sending payment failure notification for booking: {}", bookingReference, e);
        }
    }
    
    private void handlePartialPaymentRefund(String bookingReference, String transactionId) {
        try {
            Optional<Booking> bookingOpt = bookingRepository.findByBookingReference(bookingReference);
            if (bookingOpt.isEmpty()) {
                log.warn("Booking not found for partial refund: {}", bookingReference);
                return;
            }
            
            Booking booking = bookingOpt.get();
            
            // Check if there are any successful payments that need to be refunded
            boolean hasPartialPayments = booking.getPayments().stream()
                    .anyMatch(payment -> payment.getStatus() != null && 
                             payment.getStatus().equals("PARTIAL_SUCCESS"));
            
            if (hasPartialPayments) {
                // TODO: Integrate with payment service to handle partial refunds
                // For now, just create a refund request for manual processing
                String refundRequestId = refundRequestService.createRefundRequest(
                    bookingReference, 
                    "PARTIAL_PAYMENT_REFUND: Transaction " + transactionId + " failed after partial payment",
                    "SYSTEM"
                );
            }
            
        } catch (Exception e) {
            log.error("Error handling partial payment refund for booking: {}", bookingReference, e);
            throw e;
        }
    }

    private void handleSeatConfirmationSuccess(UUID sagaId, String bookingReference, List<String> confirmedSeats) {
        // Update saga state to SEATS_CONFIRMED
        updateSagaState(sagaId, SagaStep.SEATS_CONFIRMED);
        
        // Update booking status to PAID
        try {
            updateBookingStatusToPaid(bookingReference);
        } catch (Exception e) {
            log.error("Failed to update booking status for booking: {}", bookingReference, e);
        }
        
        // Trigger voucher usage if voucher was applied
        try {
            triggerVoucherUsage(sagaId, bookingReference);
        } catch (Exception e) {
            log.error("Failed to trigger voucher usage for booking: {}", bookingReference, e);
        }

        // Trigger loyalty points earning
        try {
            triggerLoyaltyPointsEarning(bookingReference);
        } catch (Exception e) {
            log.error("Failed to trigger loyalty points earning for booking: {}", bookingReference, e);
        }
        
        // Publish success event for further processing (notifications, etc.)
        publishSeatsConfirmationEvent(sagaId, bookingReference, true, 
                confirmedSeats, null, null, "All seats confirmed successfully");
        
        // Send booking confirmation notification
        try {
            sendBookingConfirmationNotification(bookingReference);
        } catch (Exception e) {
            log.error("Failed to send confirmation notification for booking: {}", bookingReference, e);
        }
        
        // Complete the saga successfully
        completeSagaSuccessfully(sagaId, bookingReference);
    }
    
    /**
     * Find saga state with retry logic to handle transaction consistency issues.
     * Sometimes saga state might not be immediately visible due to transaction isolation.
     */
    private SagaState findSagaStateWithRetry(UUID sagaId, int maxRetries, long delayMs) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.debug("[SAGA_FIND_RETRY] Attempt {} to find saga state for sagaId: {}", attempt, sagaId);
                
                // Flush any pending changes to ensure we see the latest data
                entityManager.flush();
                entityManager.clear();
                
                Optional<SagaState> sagaStateOpt = sagaStateRepository.findById(sagaId);
                if (sagaStateOpt.isPresent()) {
                    log.info("[SAGA_FIND_RETRY] Found saga state on attempt {} for sagaId: {}, step: {}", 
                            attempt, sagaId, sagaStateOpt.get().getCurrentStep());
                    return sagaStateOpt.get();
                }
                
                // If saga state not found, try to query by booking reference as fallback
                if (attempt == maxRetries / 2) {
                    log.warn("[SAGA_FIND_RETRY] Saga state not found on attempt {} for sagaId: {}, checking all saga states in DB for debugging", 
                            attempt, sagaId);
                    
                    // Debug: Count total saga states in DB
                    long totalSagaStates = sagaStateRepository.count();
                    log.warn("[SAGA_FIND_RETRY] Total saga states in DB: {}", totalSagaStates);
                    
                    // Try to find by ID again with explicit refresh
                    entityManager.flush();
                    entityManager.clear();
                    sagaStateOpt = sagaStateRepository.findById(sagaId);
                    if (sagaStateOpt.isPresent()) {
                        log.info("[SAGA_FIND_RETRY] Found saga state after refresh on attempt {} for sagaId: {}, step: {}", 
                                attempt, sagaId, sagaStateOpt.get().getCurrentStep());
                        return sagaStateOpt.get();
                    }
                }
                
                if (attempt < maxRetries) {
                    log.warn("[SAGA_FIND_RETRY] Saga state not found on attempt {} for sagaId: {}, retrying in {}ms", 
                            attempt, sagaId, delayMs);
                    Thread.sleep(delayMs);
                    // Increase delay exponentially for next attempt
                    delayMs = Math.min(delayMs * 2, 1000);
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("[SAGA_FIND_RETRY] Interrupted while retrying to find saga state for sagaId: {}", sagaId);
                break;
            } catch (Exception e) {
                log.error("[SAGA_FIND_RETRY] Error on attempt {} to find saga state for sagaId: {}", attempt, sagaId, e);
                if (attempt >= maxRetries) {
                    throw e;
                }
            }
        }
        
        log.error("[SAGA_FIND_RETRY] Failed to find saga state after {} attempts for sagaId: {}", maxRetries, sagaId);
        return null;
    }

    @RabbitListener(queues = "voucher.used.queue")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleVoucherUsed(VoucherUsedEvent event) {
        UUID sagaId = event.sagaId();
        log.info("[SAGA_EVENT][{}] Received VoucherUsedEvent. Success: {}, VoucherCode: {}", 
                sagaId, event.success(), event.originalCommand().voucherCode());
        
        try {
            if (event.success()) {
                // Voucher successfully marked as used
                updateSagaState(sagaId, SagaStep.VOUCHER_USED);
                log.info("[SAGA_STEP][{}] Voucher {} successfully marked as used", 
                        sagaId, event.originalCommand().voucherCode());
            } else {
                // Voucher usage failed, but don't fail the entire booking
                // This is not critical since the booking is already confirmed and paid
                log.warn("[SAGA_WARNING][{}] Failed to mark voucher {} as used: {}. Booking will continue.", 
                        sagaId, event.originalCommand().voucherCode(), event.failureReason());
                updateSagaState(sagaId, SagaStep.FAILED_VOUCHER_USE);
            }
            
        } catch (Exception e) {
            log.error("[SAGA_ERROR][{}] Error handling voucher used event: {}", sagaId, e.getMessage(), e);
            // Don't fail the booking - voucher usage failure is not critical at this point
        }
    }
}
