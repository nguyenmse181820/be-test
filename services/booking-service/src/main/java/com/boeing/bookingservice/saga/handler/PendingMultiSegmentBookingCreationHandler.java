package com.boeing.bookingservice.saga.handler;

import com.boeing.bookingservice.model.entity.Booking;
import com.boeing.bookingservice.saga.command.CreatePendingMultiSegmentBookingInternalCommand;
import com.boeing.bookingservice.saga.event.BookingCreatedPendingPaymentEvent;
import com.boeing.bookingservice.saga.event.CreateBookingSagaFailedEvent;
import com.boeing.bookingservice.service.BookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class PendingMultiSegmentBookingCreationHandler {

    private final BookingService bookingService;
    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.routingKeys.internalBookingCreatedEvent}")
    private String RK_INTERNAL_BOOKING_CREATED_PENDING_PAYMENT_EVENT;
    
    @Value("${app.rabbitmq.routingKeys.sagaCreateBookingFailedEvent}")
    private String RK_SAGA_CREATE_BOOKING_FAILED_EVENT;
    
    @Value("${app.rabbitmq.exchanges.events}")
    private String eventsExchange;

    @RabbitListener(queues = "${app.rabbitmq.queues.internalCreateMultiSegmentPendingBookingCmd}")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleCommand(CreatePendingMultiSegmentBookingInternalCommand command) {
        log.info("[HANDLER_ENTRY][SagaID:{}] *** MULTI-SEGMENT HANDLER CALLED *** Received CreatePendingMultiSegmentBookingInternalCommand for {} segments", 
                command.getSagaId(), command.getFlightIds().size());

        try {
            Booking createdBooking = bookingService.createPendingMultiSegmentBookingInDatabase(
                    command.getSagaId(),
                    command.getBookingReference(),
                    command.getFlightIds(),
                    command.getPassengersInfo(),
                    command.getSelectedFareName(),
                    command.getSelectedSeatsByFlight(),
                    command.getUserId(),
                    command.getTotalAmount(),
                    command.getDiscountAmount(),
                    command.getAppliedVoucherCode(),
                    command.getPaymentDeadline(),
                    command.getFlightDetails(),
                    command.getPaymentMethod(),
                    command.getClientIpAddress()
            );

            BookingCreatedPendingPaymentEvent event = BookingCreatedPendingPaymentEvent.builder()
                    .sagaId(command.getSagaId())
                    .bookingDatabaseId(createdBooking.getId())
                    .bookingReferenceDisplay(createdBooking.getBookingReference())
                    .totalAmount(createdBooking.getTotalAmount())
                    .paymentDeadline(command.getPaymentDeadline())
                    .vnpayPaymentUrl("") // Empty string as placeholder since we don't have the URL yet
                    .build();

            rabbitTemplate.convertAndSend(eventsExchange, RK_INTERNAL_BOOKING_CREATED_PENDING_PAYMENT_EVENT, event);
            log.info("[BOOKING_CMD][SagaID:{}] Published BookingCreatedPendingPaymentEvent. BookingID: {}, BookingRef: {}, Amount: {}",
                    command.getSagaId(), createdBooking.getId(), createdBooking.getBookingReference(), createdBooking.getTotalAmount());
        } catch (Exception e) {
            log.error("[BOOKING_CMD_ERROR][SagaID:{}] Failed to create multi-segment pending booking: {}", command.getSagaId(), e.getMessage(), e);
            
            CreateBookingSagaFailedEvent failedEvent = CreateBookingSagaFailedEvent.builder()
                    .sagaId(command.getSagaId())
                    .bookingReference(command.getBookingReference())
                    .failedStep("MULTI_SEGMENT_BOOKING_CREATION")
                    .failureReason("Failed to create multi-segment pending booking: " + e.getMessage())
                    .build();
                    
            rabbitTemplate.convertAndSend(eventsExchange, RK_SAGA_CREATE_BOOKING_FAILED_EVENT, failedEvent);
        }
    }
}
