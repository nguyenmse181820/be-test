package com.boeing.bookingservice.saga.handler;

import com.boeing.bookingservice.dto.request.CreatePaymentRequest;
import com.boeing.bookingservice.model.entity.Booking;
import com.boeing.bookingservice.saga.command.CreatePendingMultiSegmentBookingInternalCommand;
import com.boeing.bookingservice.saga.event.BookingCreatedPendingPaymentEvent;
import com.boeing.bookingservice.saga.event.CreateBookingSagaFailedEvent;
import com.boeing.bookingservice.service.BookingService;
import com.boeing.bookingservice.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
@Slf4j
public class PendingMultiSegmentBookingCreationHandler {

    private final BookingService bookingService;
    private final PaymentService paymentService;
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
                    command.getClientIpAddress(),
                    command.getSeatPricingByFlight(),
                    command.getBaggageAddons()
            );

            // Generate VNPay payment URL
            CreatePaymentRequest paymentCreationRequest = getCreatePaymentRequest(command, createdBooking);

            log.info("[MULTI_SEGMENT_HANDLER][{}] Creating payment request - BookingRef: {}, Amount: {}, PaymentMethod: {}", 
                    command.getSagaId(), createdBooking.getBookingReference(), createdBooking.getTotalAmount(), command.getPaymentMethod());
            
            String vnpayPaymentUrl;
            try {
                log.info("[MULTI_SEGMENT_HANDLER][{}] Calling PaymentService.createVNPayPaymentUrl with userId: {}", command.getSagaId(), command.getUserId());
                vnpayPaymentUrl = paymentService.createVNPayPaymentUrl(paymentCreationRequest, command.getUserId());
                log.info("[MULTI_SEGMENT_HANDLER][{}] PaymentService.createVNPayPaymentUrl returned: {}", command.getSagaId(), vnpayPaymentUrl);
            } catch (Exception e) {
                log.error("[MULTI_SEGMENT_HANDLER][{}] Error calling PaymentService to create VNPAY URL: {}", command.getSagaId(),
                        e.getMessage(), e);
                // Continue with empty URL for now, don't fail the booking
                vnpayPaymentUrl = "";
            }

            if (!StringUtils.hasText(vnpayPaymentUrl)) {
                log.warn("[MULTI_SEGMENT_HANDLER][{}] PaymentService returned empty VNPAY payment URL, continuing with empty URL.", command.getSagaId());
            } else {
                log.info("[MULTI_SEGMENT_HANDLER][{}] VNPAY payment URL generated: {}", command.getSagaId(), vnpayPaymentUrl);
            }

            BookingCreatedPendingPaymentEvent event = BookingCreatedPendingPaymentEvent.builder()
                    .sagaId(command.getSagaId())
                    .bookingDatabaseId(createdBooking.getId())
                    .bookingReferenceDisplay(createdBooking.getBookingReference())
                    .totalAmount(createdBooking.getTotalAmount())
                    .paymentDeadline(command.getPaymentDeadline())
                    .vnpayPaymentUrl(vnpayPaymentUrl)
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

    private static CreatePaymentRequest getCreatePaymentRequest(CreatePendingMultiSegmentBookingInternalCommand command, Booking createdBooking) {
        CreatePaymentRequest paymentCreationRequest = new CreatePaymentRequest();
        paymentCreationRequest.setBookingReference(createdBooking.getBookingReference());
        paymentCreationRequest.setAmount(createdBooking.getTotalAmount());
        paymentCreationRequest.setOrderDescription("Thanh toan don hang " + createdBooking.getBookingReference());
        paymentCreationRequest.setPaymentMethod(command.getPaymentMethod());
        paymentCreationRequest.setClientIpAddress(command.getClientIpAddress());
        return paymentCreationRequest;
    }
}
