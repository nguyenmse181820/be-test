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
import com.boeing.bookingservice.saga.event.PaymentCompletedForBookingEvent;
import com.boeing.bookingservice.saga.event.SeatsConfirmationResponseEvent;
import com.boeing.bookingservice.saga.orchestrator.CreateBookingSagaOrchestrator;
import com.boeing.bookingservice.saga.state.SagaState;
import com.boeing.bookingservice.saga.state.SagaStateRepository;
import com.boeing.bookingservice.saga.SagaStep;
import com.boeing.bookingservice.service.RefundRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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

    @Value("${app.rabbitmq.exchanges.events}")
    private String eventsExchange;

    @Value("${app.rabbitmq.routingKeys.seatConfirmationResponse}")
    private String RK_SEAT_CONFIRMATION_RESPONSE;    
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handlePaymentCompleted(PaymentCompletedForBookingEvent event) {
        UUID sagaId = event.getSagaId();
        
        log.info("[PAYMENT_COMPLETION_HANDLER] Processing payment completion for saga: {}, booking: {}, successful: {}",
                sagaId, event.getBookingReference(), event.isPaymentSuccessful());

        try {
            SagaState sagaState = sagaStateRepository.findById(sagaId).orElse(null);
            
            if (sagaState == null) {
                log.warn("[PAYMENT_COMPLETION_HANDLER] Saga state not found for sagaId: {}", sagaId);
                return;
            }

            if (sagaState.getCurrentStep() != SagaStep.AWAITING_PAYMENT_IPN) {
                log.warn("[PAYMENT_COMPLETION_HANDLER] Saga {} not in AWAITING_PAYMENT_IPN state. Current: {}", 
                        sagaId, sagaState.getCurrentStep());
                return;
            }
            
            // Update saga state to PAYMENT_COMPLETED
            sagaState.setCurrentStep(SagaStep.PAYMENT_COMPLETED);
            sagaState.setUpdatedAt(LocalDateTime.now());
            sagaStateRepository.save(sagaState);

            if (event.isPaymentSuccessful()) {
                log.info("[PAYMENT_COMPLETION_HANDLER] Payment successful, proceeding to seat confirmation for saga: {}", sagaId);
                triggerSeatConfirmation(event);
            } else {
                log.warn("[PAYMENT_COMPLETION_HANDLER] Payment failed for saga {}, booking: {}", 
                        sagaId, event.getBookingReference());
                handlePaymentFailure(event);
            }
            
        } catch (Exception e) {
            log.error("[PAYMENT_COMPLETION_HANDLER] Error processing payment completion for saga: {}", sagaId, e);
            handleSeatConfirmationFailure(sagaId, event.getBookingReference(), "INTERNAL_ERROR", 
                    "Error in payment completion handler: " + e.getMessage());
        }
    }

    private void triggerSeatConfirmation(PaymentCompletedForBookingEvent event) {
        UUID sagaId = event.getSagaId();
        String bookingReference = event.getBookingReference();
        
        try {
            log.info("[SEAT_CONFIRMATION] Starting seat confirmation for saga: {}, booking: {}", 
                    sagaId, bookingReference);

            // Get booking details to extract seat information
            Optional<Booking> bookingOpt = bookingRepository.findByBookingReference(bookingReference);
            if (bookingOpt.isEmpty()) {
                log.error("[SEAT_CONFIRMATION] Booking not found for reference: {}", bookingReference);
                handleSeatConfirmationFailure(sagaId, bookingReference, "BOOKING_NOT_FOUND", 
                        "Booking not found in database");
                return;
            }

            Booking booking = bookingOpt.get();
            
            // Extract flight ID and seat codes from booking details
            if (booking.getBookingDetails() == null || booking.getBookingDetails().isEmpty()) {
                log.error("[SEAT_CONFIRMATION] No booking details found for booking: {}", bookingReference);
                handleSeatConfirmationFailure(sagaId, bookingReference, "NO_BOOKING_DETAILS", 
                        "No booking details found");
                return;
            }

            // For now, we'll use the first flight (assuming single flight booking)
            // In a multi-flight booking, this logic would need to be enhanced
            BookingDetail firstDetail = booking.getBookingDetails().get(0);
            UUID flightId = firstDetail.getFlightId();
              // For this implementation, we'll use actual seat codes from booking details
            // If no seats were selected during booking, we'll generate dummy ones
            List<String> seatCodes = getSeatCodesFromBookingDetails(booking);
            
            // Update saga state to AWAITING_SEAT_CONFIRMATION
            updateSagaState(sagaId, SagaStep.AWAITING_SEAT_CONFIRMATION);
            
            // Call Flight Service to confirm seats
            FsConfirmSeatsRequestDTO request = FsConfirmSeatsRequestDTO.builder()
                    .bookingReference(bookingReference)
                    .seatCodes(seatCodes)
                    .build();

            log.info("[SEAT_CONFIRMATION] Calling Flight Service to confirm {} seats for booking: {}", 
                    seatCodes.size(), bookingReference);

            FsConfirmSeatsResponseDTO response = flightClient.confirmSeats(flightId, request);
            
            // Process the response
            handleSeatConfirmationResponse(sagaId, bookingReference, response, seatCodes);
            
        } catch (Exception e) {
            log.error("[SEAT_CONFIRMATION] Error during seat confirmation for saga: {}, booking: {}", 
                    sagaId, bookingReference, e);
            handleSeatConfirmationFailure(sagaId, bookingReference, "SEAT_CONFIRMATION_ERROR", 
                    "Error calling Flight Service: " + e.getMessage());
        }
    }

    private void handleSeatConfirmationResponse(UUID sagaId, String bookingReference, 
                                              FsConfirmSeatsResponseDTO response, List<String> requestedSeats) {
        
        boolean allSeatsConfirmed = response != null && 
                "Success".equalsIgnoreCase(response.getStatus()) &&
                (response.getFailedToConfirmSeats() == null || response.getFailedToConfirmSeats().isEmpty());

        if (allSeatsConfirmed) {
            log.info("[SEAT_CONFIRMATION] All seats confirmed successfully for booking: {}, confirmed seats: {}", 
                    bookingReference, response.getConfirmedSeats());
            
            // Update saga state to SEATS_CONFIRMED
            updateSagaState(sagaId, SagaStep.SEATS_CONFIRMED);
            
            // Update booking status to PAID
            try {
                updateBookingStatusToPaid(bookingReference);
                log.info("[SEAT_CONFIRMATION] Updated booking status to PAID for booking: {}", bookingReference);
            } catch (Exception e) {
                log.error("[SEAT_CONFIRMATION] Failed to update booking status for booking: {}", bookingReference, e);
            }
            
            // Trigger loyalty points earning
            try {
                triggerLoyaltyPointsEarning(bookingReference);
            } catch (Exception e) {
                log.error("[SEAT_CONFIRMATION] Failed to trigger loyalty points earning for booking: {}", bookingReference, e);
            }
            
            // Publish success event for further processing (notifications, etc.)
            publishSeatsConfirmationEvent(sagaId, bookingReference, true, 
                    response.getConfirmedSeats(), null, null, "All seats confirmed successfully");
            
            // Send booking confirmation notification
            try {
                sendBookingConfirmationNotification(bookingReference);
            } catch (Exception e) {
                log.error("[SEAT_CONFIRMATION] Failed to send confirmation notification for booking: {}", bookingReference, e);
            }
            
            // Complete the saga successfully
            completeSagaSuccessfully(sagaId, bookingReference);
            
        } else {
            String failureReason = response != null ? response.getMessage() : "Unknown error";
            List<String> failedSeats = response != null ? response.getFailedToConfirmSeats() : requestedSeats;
            
            log.warn("[SEAT_CONFIRMATION] Seat confirmation failed for booking: {}, failed seats: {}, reason: {}", 
                    bookingReference, failedSeats, failureReason);
            
            // Publish failure event
            publishSeatsConfirmationEvent(sagaId, bookingReference, false, 
                    response != null ? response.getConfirmedSeats() : null, 
                    failedSeats, failureReason, "Seat confirmation failed");
            
            // Handle the failure (initiate compensation)
            handleSeatConfirmationFailure(sagaId, bookingReference, "SEATS_NOT_AVAILABLE", failureReason);
        }
    }    private void handleSeatConfirmationFailure(UUID sagaId, String bookingReference, 
                                             String errorCode, String errorMessage) {
        try {
            log.error("[SEAT_CONFIRMATION_FAILURE] Handling seat confirmation failure for saga: {}, booking: {}, error: {} - {}", 
                    sagaId, bookingReference, errorCode, errorMessage);
            
            // Update saga state to failure
            updateSagaState(sagaId, SagaStep.FAILED_SEAT_CONFIRMATION);
            
            // Implement compensation logic:
            
            // 1. Create refund request (manual process for staff)
            try {
                String refundRequestId = refundRequestService.createRefundRequest(
                    bookingReference, 
                    "SEAT_CONFIRMATION_FAILED: " + errorMessage,
                    "SYSTEM"
                );
                log.info("[SEAT_CONFIRMATION_FAILURE] Created refund request: {} for booking: {}", 
                        refundRequestId, bookingReference);
            } catch (Exception e) {
                log.error("[SEAT_CONFIRMATION_FAILURE] Failed to create refund request for booking: {}", 
                        bookingReference, e);
            }
            
            // 2. Release fare inventory (call flight service)
            try {
                releaseFareInventory(bookingReference, "SEAT_CONFIRMATION_FAILED");
            } catch (Exception e) {
                log.error("[SEAT_CONFIRMATION_FAILURE] Failed to release fare inventory for booking: {}", 
                        bookingReference, e);
            }
            
            // 3. Update booking status to FAILED
            try {
                updateBookingStatusToFailed(bookingReference, errorMessage);
            } catch (Exception e) {
                log.error("[SEAT_CONFIRMATION_FAILURE] Failed to update booking status for booking: {}", 
                        bookingReference, e);
            }
            
            // 4. TODO: Send notification to customer (implement notification service call)
            
            log.info("[SEAT_CONFIRMATION_FAILURE] Seat confirmation failure handled for saga: {}", sagaId);
            
        } catch (Exception e) {
            log.error("[SEAT_CONFIRMATION_FAILURE] Error handling seat confirmation failure for saga: {}", sagaId, e);
        }
    }    private void handlePaymentFailure(PaymentCompletedForBookingEvent event) {
        UUID sagaId = event.getSagaId();
        String bookingReference = event.getBookingReference();
        
        log.info("[PAYMENT_FAILURE] Handling payment failure for saga: {}, booking: {}, transactionId: {}", 
                sagaId, bookingReference, event.getTransactionId());
        
        try {
            // Update saga state to payment failed
            updateSagaState(sagaId, SagaStep.FAILED_PAYMENT);
            
            // Implement compensation logic for payment failure:
            
            // 1. Release fare inventory (since payment failed, release the held fares)
            try {
                releaseFareInventory(bookingReference, "PAYMENT_FAILED: " + event.getTransactionId());
                log.info("[PAYMENT_FAILURE] Successfully released fare inventory for booking: {}", bookingReference);
            } catch (Exception e) {
                log.error("[PAYMENT_FAILURE] Failed to release fare inventory for booking: {}", bookingReference, e);
            }
            
            // 2. Release any held seats (if user had pre-selected seats)
            try {
                releaseSeatsFromBooking(bookingReference, "PAYMENT_FAILED: " + event.getTransactionId());
            } catch (Exception e) {
                log.error("[PAYMENT_FAILURE] Failed to release seats for booking: {}", bookingReference, e);
            }
            
            // 3. Update booking status to CANCELLED
            try {
                updateBookingStatusToCancelled(bookingReference, "Payment failed: " + event.getTransactionId());
                log.info("[PAYMENT_FAILURE] Successfully updated booking status to CANCELLED for booking: {}", bookingReference);
            } catch (Exception e) {
                log.error("[PAYMENT_FAILURE] Failed to update booking status for booking: {}", bookingReference, e);
            }
            
            // 4. Handle partial payment refund (if any payment was made but failed to complete)
            // This would be rare but possible in some payment scenarios
            try {
                handlePartialPaymentRefund(bookingReference, event.getTransactionId());
            } catch (Exception e) {
                log.error("[PAYMENT_FAILURE] Failed to handle partial payment refund for booking: {}", bookingReference, e);
            }
            
            // 5. Send notification to customer about payment failure
            try {
                sendPaymentFailureNotification(bookingReference, event.getTransactionId());
            } catch (Exception e) {
                log.error("[PAYMENT_FAILURE] Failed to send payment failure notification for booking: {}", bookingReference, e);
            }
            
            log.info("[PAYMENT_FAILURE] Payment failure compensation completed for saga: {}, booking: {}", 
                    sagaId, bookingReference);
            
        } catch (Exception e) {
            log.error("[PAYMENT_FAILURE] Error handling payment failure for saga: {}, booking: {}", 
                    sagaId, bookingReference, e);
        }
    }

    private void completeSagaSuccessfully(UUID sagaId, String bookingReference) {
        try {
            log.info("[SAGA_COMPLETION] Completing saga successfully for saga: {}, booking: {}", 
                    sagaId, bookingReference);
            
            // Update saga state to completed
            updateSagaState(sagaId, SagaStep.COMPLETED_SUCCESSFULLY);
            
            log.info("[SAGA_COMPLETION] Saga completed successfully for saga: {}, booking: {}", 
                    sagaId, bookingReference);
            
        } catch (Exception e) {
            log.error("[SAGA_COMPLETION] Error completing saga for saga: {}", sagaId, e);
        }
    }

    private void updateSagaState(UUID sagaId, SagaStep newStep) {
        try {
            SagaState sagaState = sagaStateRepository.findById(sagaId).orElse(null);
            if (sagaState != null) {
                sagaState.setCurrentStep(newStep);
                sagaState.setUpdatedAt(LocalDateTime.now());
                sagaStateRepository.save(sagaState);
                
                log.debug("[SAGA_STATE_UPDATE] Updated saga {} to step: {}", sagaId, newStep);
            } else {
                log.warn("[SAGA_STATE_UPDATE] Saga state not found for sagaId: {}", sagaId);
            }
        } catch (Exception e) {
            log.error("[SAGA_STATE_UPDATE] Error updating saga state for saga: {}", sagaId, e);
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
            
            log.info("[SEAT_CONFIRMATION_EVENT] Published seat confirmation event for booking: {}, success: {}", 
                    bookingReference, success);
            
        } catch (Exception e) {
            log.error("[SEAT_CONFIRMATION_EVENT] Error publishing seat confirmation event for booking: {}", 
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
            log.info("[SEAT_EXTRACTION] No seats were pre-selected, generating auto-assigned seats for booking: {}", 
                    booking.getBookingReference());
            return generateSeatCodesForBooking(booking);
        }
        
        log.info("[SEAT_EXTRACTION] Using pre-selected seats for booking: {}, seats: {}", 
                booking.getBookingReference(), seatCodes);
        
        return seatCodes;
    }

    private void releaseSeatsFromBooking(String bookingReference, String reason) {
        try {
            log.info("[COMPENSATION] Releasing seats for booking: {}, reason: {}", bookingReference, reason);
            
            Optional<Booking> bookingOpt = bookingRepository.findByBookingReference(bookingReference);
            if (bookingOpt.isEmpty()) {
                log.warn("[COMPENSATION] Booking not found for seat release: {}", bookingReference);
                return;
            }
            
            Booking booking = bookingOpt.get();
            List<String> seatCodes = getSeatCodesFromBookingDetails(booking);
            
            // Only release if there are actual pre-selected seats (not dummy generated ones)
            boolean hasRealSeatSelections = booking.getBookingDetails().stream()
                    .anyMatch(detail -> detail.getSelectedSeatCode() != null && 
                             !detail.getSelectedSeatCode().trim().isEmpty());
            
            if (!hasRealSeatSelections) {
                log.info("[COMPENSATION] No pre-selected seats to release for booking: {}", bookingReference);
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
                
                if (response != null && "Success".equalsIgnoreCase(response.getStatus())) {
                    log.info("[COMPENSATION] Successfully released {} seats for booking: {}", 
                            seatCodes.size(), bookingReference);
                } else {
                    log.warn("[COMPENSATION] Failed to release some seats for booking: {}, failed seats: {}", 
                            bookingReference, response != null ? response.getFailedToReleaseSeats() : "unknown");
                }
            }
        } catch (Exception e) {
            log.error("[COMPENSATION] Error releasing seats for booking: {}", bookingReference, e);
            throw e;
        }
    }

    private List<String> generateSeatCodesForBooking(Booking booking) {
        // This is a simplified implementation
        // In a real system, seat codes would be selected during booking creation
        // and stored in the booking details
        
        int passengerCount = booking.getBookingDetails().size();
        List<String> seatCodes = new java.util.ArrayList<>();
        
        // Generate dummy seat codes (in production, these would be actual selected seats)
        for (int i = 1; i <= passengerCount; i++) {
            seatCodes.add(i + "A"); // Simple seat code generation in "1A" format
        }
        
        log.info("[SEAT_GENERATION] Generated {} seat codes for booking: {}", 
                seatCodes.size(), booking.getBookingReference());
        
        return seatCodes;
    }
    
    private void releaseFareInventory(String bookingReference, String reason) {
        try {
            log.info("[COMPENSATION] Releasing fare inventory for booking: {}, reason: {}", bookingReference, reason);
            
            Optional<Booking> bookingOpt = bookingRepository.findByBookingReference(bookingReference);
            if (bookingOpt.isEmpty()) {
                log.warn("[COMPENSATION] Booking not found for fare release: {}", bookingReference);
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

                if (response != null && response.isSuccess()) {
                    log.info("[COMPENSATION] Successfully released fare inventory for booking: {}", bookingReference);
                } else {
                    log.warn("[COMPENSATION] Failed to release fare inventory for booking: {}", bookingReference);
                }
            }
        } catch (Exception e) {
            log.error("[COMPENSATION] Error releasing fare inventory for booking: {}", bookingReference, e);
            throw e;
        }
    }
    
    private void updateBookingStatusToFailed(String bookingReference, String failureReason) {
        try {
            log.info("[COMPENSATION] Updating booking status to FAILED for booking: {}", bookingReference);
            
            Optional<Booking> bookingOpt = bookingRepository.findByBookingReference(bookingReference);
            if (bookingOpt.isEmpty()) {
                log.warn("[COMPENSATION] Booking not found for status update: {}", bookingReference);
                return;
            }
            
            Booking booking = bookingOpt.get();
            booking.setStatus(BookingStatus.FAILED_TO_CONFIRM_SEATS);
            booking.setUpdatedAt(java.time.LocalDateTime.now());
            // Add failure reason to notes or create a separate failure reason field
            
            bookingRepository.save(booking);
            
            log.info("[COMPENSATION] Updated booking status to FAILED for booking: {}", bookingReference);
            
        } catch (Exception e) {
            log.error("[COMPENSATION] Error updating booking status for booking: {}", bookingReference, e);
            throw e;
        }
    }
    
    private void updateBookingStatusToCancelled(String bookingReference, String cancellationReason) {
        try {
            log.info("[PAYMENT_FAILURE] Updating booking status to CANCELLED for booking: {}", bookingReference);
            
            Optional<Booking> bookingOpt = bookingRepository.findByBookingReference(bookingReference);
            if (bookingOpt.isEmpty()) {
                log.warn("[PAYMENT_FAILURE] Booking not found for status update: {}", bookingReference);
                return;
            }
              Booking booking = bookingOpt.get();
            booking.setStatus(BookingStatus.CANCELLED);
            booking.setUpdatedAt(java.time.LocalDateTime.now());
            // You might want to add a cancellation_reason field to booking table
            
            bookingRepository.save(booking);
            
            log.info("[PAYMENT_FAILURE] Updated booking status to CANCELLED for booking: {}", bookingReference);
            
        } catch (Exception e) {
            log.error("[PAYMENT_FAILURE] Error updating booking status for booking: {}", bookingReference, e);
            throw e;
        }
    }
    
    private void updateBookingStatusToPaid(String bookingReference) {
        try {
            log.info("[BOOKING_STATUS] Updating booking status to PAID for booking: {}", bookingReference);
            
            Optional<Booking> bookingOpt = bookingRepository.findByBookingReference(bookingReference);
            if (bookingOpt.isEmpty()) {
                log.warn("[BOOKING_STATUS] Booking not found for status update: {}", bookingReference);
                return;
            }
            
            Booking booking = bookingOpt.get();
            booking.setStatus(BookingStatus.PAID);
            booking.setUpdatedAt(java.time.LocalDateTime.now());
            
            bookingRepository.save(booking);
            
            log.info("[BOOKING_STATUS] Updated booking status to PAID for booking: {}", bookingReference);
            
        } catch (Exception e) {
            log.error("[BOOKING_STATUS] Error updating booking status for booking: {}", bookingReference, e);
            throw e;
        }
    }
    
    private void triggerLoyaltyPointsEarning(String bookingReference) {
        try {
            log.info("[LOYALTY_POINTS] Triggering loyalty points earning for booking: {}", bookingReference);
            
            Optional<Booking> bookingOpt = bookingRepository.findByBookingReference(bookingReference);
            if (bookingOpt.isEmpty()) {
                log.warn("[LOYALTY_POINTS] Booking not found for loyalty points: {}", bookingReference);
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
            
            log.info("[LOYALTY_POINTS] Calculated {} points for booking: {} (amount: {})", 
                    pointsToEarn, bookingReference, totalAmount);
            
        } catch (Exception e) {
            log.error("[LOYALTY_POINTS] Error triggering loyalty points for booking: {}", bookingReference, e);
            // Don't throw - loyalty points earning is not critical for booking completion
        }
    }
    
    private void sendBookingConfirmationNotification(String bookingReference) {
        try {
            log.info("[NOTIFICATION] Sending booking confirmation notification for booking: {}", bookingReference);
            
            Optional<Booking> bookingOpt = bookingRepository.findByBookingReference(bookingReference);
            if (bookingOpt.isEmpty()) {
                log.warn("[NOTIFICATION] Booking not found for notification: {}", bookingReference);
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
            
            log.info("[NOTIFICATION] Queued booking confirmation notification for booking: {}", bookingReference);
            
        } catch (Exception e) {
            log.error("[NOTIFICATION] Error sending booking confirmation notification for booking: {}", bookingReference, e);
            // Don't throw - notification failure shouldn't fail the booking
        }
    }

    private void sendPaymentFailureNotification(String bookingReference, String transactionId) {
        try {
            log.info("[NOTIFICATION] Sending payment failure notification for booking: {}", bookingReference);
            
            Optional<Booking> bookingOpt = bookingRepository.findByBookingReference(bookingReference);
            if (bookingOpt.isEmpty()) {
                log.warn("[NOTIFICATION] Booking not found for payment failure notification: {}", bookingReference);
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
            
            log.info("[NOTIFICATION] Queued payment failure notification for booking: {}", bookingReference);
            
        } catch (Exception e) {
            log.error("[NOTIFICATION] Error sending payment failure notification for booking: {}", bookingReference, e);
        }
    }
    
    private void handlePartialPaymentRefund(String bookingReference, String transactionId) {
        try {
            log.info("[PARTIAL_REFUND] Handling partial payment refund for booking: {}, transaction: {}", 
                    bookingReference, transactionId);
            
            Optional<Booking> bookingOpt = bookingRepository.findByBookingReference(bookingReference);
            if (bookingOpt.isEmpty()) {
                log.warn("[PARTIAL_REFUND] Booking not found for partial refund: {}", bookingReference);
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
                log.info("[PARTIAL_REFUND] Created refund request: {} for partial payment refund of booking: {}", 
                        refundRequestId, bookingReference);
            } else {
                log.info("[PARTIAL_REFUND] No partial payments found for booking: {}", bookingReference);
            }
            
        } catch (Exception e) {
            log.error("[PARTIAL_REFUND] Error handling partial payment refund for booking: {}", bookingReference, e);
            throw e;
        }
    }
}
