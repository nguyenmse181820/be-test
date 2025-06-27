package com.boeing.bookingservice.event.listener;

import com.boeing.bookingservice.event.PaymentProcessedEvent;
import com.boeing.bookingservice.event.PaymentFailedEvent;
import com.boeing.bookingservice.saga.handler.PaymentCompletionHandler;
import com.boeing.bookingservice.saga.event.PaymentCompletedForBookingEvent;
import com.boeing.bookingservice.service.BookingService;
import com.boeing.bookingservice.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventListener {
    
    private final BookingService bookingService;
    private final BookingRepository bookingRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PaymentCompletionHandler paymentCompletionHandler;

    @EventListener
    @Async
    public void handlePaymentProcessedEvent(PaymentProcessedEvent event) {
        try {
            bookingService.updateBookingStatusAndDetailsPostPayment(
                event.getBookingReference(),
                event.isPaymentSuccessful(),
                event.getTransactionIdOrError()
            );            if (event.isPaymentSuccessful()) {
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
                        UUID sagaId = deriveSagaIdFromBooking(booking);
                        PaymentCompletedForBookingEvent sagaEvent = new PaymentCompletedForBookingEvent(
                                this,
                                sagaId,
                                event.getBookingReference(),
                                booking.getId(),
                                event.isPaymentSuccessful(),
                                event.getTransactionIdOrError()
                        );
                    }, () -> {
                        log.warn("[SAGA_COMPLETION] Booking not found for reference: {}", event.getBookingReference());
                    });
        } catch (Exception e) {
            log.error("[SAGA_COMPLETION] Error triggering saga completion for booking: {}", 
                      event.getBookingReference(), e);
        }
    }    private UUID deriveSagaIdFromBooking(Object booking) {
        // For now, we'll use the booking ID as saga ID since we don't have a separate saga table
        // In a proper implementation, you should have a saga state table that maps bookings to saga instances
        if (booking instanceof com.boeing.bookingservice.model.entity.Booking) {
            com.boeing.bookingservice.model.entity.Booking bookingEntity = (com.boeing.bookingservice.model.entity.Booking) booking;
            // Use booking ID as saga ID for simplicity
            // In production, consider using a proper saga state management approach
            log.info("[SAGA_ID_DERIVATION] Using booking ID {} as saga ID for booking reference: {}", 
                    bookingEntity.getId(), bookingEntity.getBookingReference());
            return bookingEntity.getId();
        }

        log.warn("[SAGA_ID_DERIVATION] Unable to derive saga ID from booking, using random UUID. Booking type: {}", 
                booking != null ? booking.getClass().getSimpleName() : "null");
        return UUID.randomUUID();
    }
}
