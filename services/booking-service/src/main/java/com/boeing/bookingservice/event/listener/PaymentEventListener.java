package com.boeing.bookingservice.event.listener;

import com.boeing.bookingservice.event.PaymentProcessedEvent;
import com.boeing.bookingservice.event.PaymentFailedEvent;
import com.boeing.bookingservice.saga.handler.PaymentCompletionHandler;
import com.boeing.bookingservice.saga.event.PaymentCompletedForBookingEvent;
import com.boeing.bookingservice.saga.state.SagaState;
import com.boeing.bookingservice.saga.state.SagaStateRepository;
import com.boeing.bookingservice.service.BookingService;
import com.boeing.bookingservice.service.impl.BookingServiceImpl;
import com.boeing.bookingservice.repository.BookingRepository;
import com.boeing.bookingservice.model.entity.Booking;
import com.boeing.bookingservice.model.enums.BookingStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventListener {
    
    private final BookingService bookingService;
    private final BookingServiceImpl bookingServiceImpl;
    private final BookingRepository bookingRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PaymentCompletionHandler paymentCompletionHandler;
    private final SagaStateRepository sagaStateRepository;

    @EventListener
    @Async
    public void handlePaymentProcessedEvent(PaymentProcessedEvent event) {
        try {
            bookingService.updateBookingStatusAndDetailsPostPayment(
                event.getBookingReference(),
                event.isPaymentSuccessful(),
                event.getTransactionIdOrError()
            );            
            if (event.isPaymentSuccessful()) {
                // Loyalty points are now awarded in BookingCompletedEventListener to avoid duplication
                log.info("Payment successful for booking: {}. Loyalty points will be awarded by BookingCompletedEventListener.", 
                        event.getBookingReference());
                
                bookingService.handlePostPaymentWorkflow(event.getBookingReference());
                triggerSagaCompletion(event);
            } else {
                publishPaymentFailedEvent(event);
                triggerSagaCompletion(event);
            }
        } catch (Exception e) {
            log.error("[PAYMENT_EVENT_LISTENER] Error processing PaymentProcessedEvent for booking: {}", 
                      event.getBookingReference(), e);
            // In a production system, you might want to publish a compensation event
            // or store this in a dead letter queue for retry
        }
    }
    
    private void publishPaymentFailedEvent(PaymentProcessedEvent event) {
        try {
            bookingRepository.findByBookingReference(event.getBookingReference())
                    .ifPresent(booking -> {
                        PaymentFailedEvent failedEvent = new PaymentFailedEvent(
                                this,
                                event.getBookingReference(),
                                booking.getId(),
                                booking.getUserId(),
                                event.getTransactionIdOrError(),
                                "VNPAY"
                        );
                        
                        eventPublisher.publishEvent(failedEvent);
                    });
        } catch (Exception e) {
            log.error("[PAYMENT_FAILED_EVENT] Error publishing PaymentFailedEvent for booking: {}", 
                      event.getBookingReference(), e);
        }
    }
      private void triggerSagaCompletion(PaymentProcessedEvent event) {
        try {
            bookingRepository.findByBookingReference(event.getBookingReference())
                    .ifPresentOrElse(booking -> {
                        UUID sagaId = findCorrectSagaIdForBooking(event.getBookingReference(), booking);
                        PaymentCompletedForBookingEvent sagaEvent = new PaymentCompletedForBookingEvent(
                                this,
                                sagaId,
                                event.getBookingReference(),
                                booking.getId(),
                                event.isPaymentSuccessful(),
                                event.getTransactionIdOrError()
                        );
                        
                        log.info("[SAGA_COMPLETION] Publishing PaymentCompletedForBookingEvent for booking: {}, sagaId: {}, paymentSuccessful: {}", 
                                event.getBookingReference(), sagaId, event.isPaymentSuccessful());
                        eventPublisher.publishEvent(sagaEvent);
                    }, () -> {
                        log.warn("[SAGA_COMPLETION] Booking not found for reference: {}", event.getBookingReference());
                    });
        } catch (Exception e) {
            log.error("[SAGA_COMPLETION] Error triggering saga completion for booking: {}", 
                      event.getBookingReference(), e);
        }
    }    /**
     * Find the correct sagaId for a booking by searching the saga_state table.
     * This method handles the case where the booking ID might differ from the sagaId
     * due to ID generation timing or other issues.
     */
    private UUID findCorrectSagaIdForBooking(String bookingReference, Object booking) {
        try {
            log.info("[SAGA_ID_LOOKUP] Looking up sagaId for booking reference: {}", bookingReference);
            
            // First approach: try to find saga state by booking reference in the payload
            // This is the most reliable method since the booking reference is stored in the saga payload
            List<SagaState> allSagaStates = sagaStateRepository.findAll();
            
            for (SagaState sagaState : allSagaStates) {
                if (sagaState.getPayloadJson() != null && sagaState.getPayloadJson().contains(bookingReference)) {
                    log.info("[SAGA_ID_LOOKUP] Found sagaId {} for booking reference {} by searching saga state payloads", 
                            sagaState.getSagaId(), bookingReference);
                    return sagaState.getSagaId();
                }
            }
            
            // Fallback approach: use booking ID as sagaId (original logic)
            // This may work if the IDs were set correctly during creation
            if (booking instanceof com.boeing.bookingservice.model.entity.Booking) {
                com.boeing.bookingservice.model.entity.Booking bookingEntity = (com.boeing.bookingservice.model.entity.Booking) booking;
                UUID bookingId = bookingEntity.getId();
                
                // Check if a saga state exists with this booking ID as sagaId
                Optional<SagaState> sagaStateOpt = sagaStateRepository.findById(bookingId);
                if (sagaStateOpt.isPresent()) {
                    log.info("[SAGA_ID_LOOKUP] Found sagaId {} for booking reference {} using booking ID", 
                            bookingId, bookingReference);
                    return bookingId;
                } else {
                    log.warn("[SAGA_ID_LOOKUP] No saga state found with booking ID {} as sagaId for booking reference {}", 
                            bookingId, bookingReference);
                }
            }
            
            log.error("[SAGA_ID_LOOKUP] Could not find sagaId for booking reference: {}. Available saga states: {}", 
                    bookingReference, allSagaStates.size());
            
            // Log some debug information
            log.error("[SAGA_ID_LOOKUP] Recent saga states:");
            allSagaStates.stream()
                    .limit(5)
                    .forEach(state -> log.error("[SAGA_ID_LOOKUP]   - sagaId: {}, step: {}, created: {}", 
                            state.getSagaId(), state.getCurrentStep(), state.getCreatedAt()));
            
            throw new IllegalStateException("Cannot find sagaId for booking reference: " + bookingReference);
            
        } catch (Exception e) {
            log.error("[SAGA_ID_LOOKUP] Error looking up sagaId for booking reference: {}", bookingReference, e);
            throw e;
        }
    }

    private UUID deriveSagaIdFromBooking(Object booking) {
        // DEPRECATED: This method is no longer reliable due to booking ID vs sagaId mismatch issues
        // Use findCorrectSagaIdForBooking instead
        log.warn("[SAGA_ID_DERIVATION] DEPRECATED: deriveSagaIdFromBooking called. Use findCorrectSagaIdForBooking instead.");
        
        if (booking instanceof com.boeing.bookingservice.model.entity.Booking) {
            com.boeing.bookingservice.model.entity.Booking bookingEntity = (com.boeing.bookingservice.model.entity.Booking) booking;
            log.info("[SAGA_ID_DERIVATION] Using booking ID {} as saga ID for booking reference: {}", 
                    bookingEntity.getId(), bookingEntity.getBookingReference());
            return bookingEntity.getId();
        }

        log.warn("[SAGA_ID_DERIVATION] Unable to derive saga ID from booking, using random UUID. Booking type: {}", 
                booking != null ? booking.getClass().getSimpleName() : "null");
        return UUID.randomUUID();
    }
    
    // Removed awardLoyaltyPointsForSuccessfulPayment method to prevent duplicate loyalty points
    // Loyalty points are now exclusively awarded in BookingCompletedEventListener
}
