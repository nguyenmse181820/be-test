package com.boeing.bookingservice.saga.handler;

import com.boeing.bookingservice.dto.request.CreatePaymentRequest;
import com.boeing.bookingservice.exception.BadRequestException;
import com.boeing.bookingservice.exception.ResourceNotFoundException;
import com.boeing.bookingservice.exception.SagaProcessingException;
import com.boeing.bookingservice.model.entity.Booking;
import com.boeing.bookingservice.saga.SagaStep;
import com.boeing.bookingservice.saga.command.CreatePendingBookingInternalCommand;
import com.boeing.bookingservice.saga.event.BookingCreatedPendingPaymentEvent;
import com.boeing.bookingservice.saga.orchestrator.CreateBookingSagaOrchestrator;
import com.boeing.bookingservice.saga.state.SagaState;
import com.boeing.bookingservice.saga.state.SagaStateRepository;
import com.boeing.bookingservice.service.BookingService;
import com.boeing.bookingservice.service.PaymentService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class PendingBookingCreationHandler {

    private final BookingService bookingService;
    private final PaymentService paymentService;
    private final RabbitTemplate rabbitTemplate;
    private final SagaStateRepository sagaStateRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.rabbitmq.exchanges.events}")
    private String eventsExchange;

    @Value("${app.rabbitmq.routingKeys.internalBookingCreatedEvent}")
    private String RK_INTERNAL_BOOKING_CREATED_PENDING_PAYMENT_EVENT;

    @RabbitListener(queues = "${app.rabbitmq.queues.internalCreatePendingBookingCmd}")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleCreatePendingBooking(CreatePendingBookingInternalCommand command) {
        UUID sagaId = command.getSagaId();
        log.info(
                "[SAGA_CMD_HANDLER][{}] Received CreatePendingBookingInternalCommand for BookingRef: {}, Total amount: {}",
                sagaId, command.getBookingReference(), command.getTotalAmount());

        SagaState sagaState = loadSagaState(sagaId);
        CreateBookingSagaOrchestrator.CreateBookingSagaPayload payload = deserializePayload(sagaState.getPayloadJson());

        try {            
                Booking createdBooking = bookingService.createPendingBookingInDatabase(
                    sagaId,
                    command.getBookingReference(),
                    command.getFlightId(),
                    command.getPassengersInfo(),
                    command.getSelectedFareName(),
                    command.getSeatSelections(),
                    command.getUserId(),
                    command.getTotalAmount(),
                    command.getDiscountAmount(),
                    command.getAppliedVoucherCode(),
                    command.getPaymentDeadline(),
                    command.getSnapshotFlightCode(),
                    command.getSnapshotOriginAirportCode(),
                    command.getSnapshotDestinationAirportCode(),
                    command.getSnapshotDepartureTime(),
                    command.getSnapshotArrivalTime(),
                    command.getPaymentMethod(),
                    command.getClientIpAddress(),
                    command.getBaggageAddons());
            log.info("[SAGA_CMD_HANDLER][{}] Pending booking entities created in DB. Booking DB ID: {}", sagaId,
                    createdBooking.getId());

            CreatePaymentRequest paymentCreationRequest = new CreatePaymentRequest();
            paymentCreationRequest.setBookingReference(createdBooking.getBookingReference());
            paymentCreationRequest.setAmount(createdBooking.getTotalAmount());
            paymentCreationRequest.setOrderDescription("Thanh toan don hang " + createdBooking.getBookingReference());
            paymentCreationRequest.setPaymentMethod(command.getPaymentMethod());
            paymentCreationRequest.setClientIpAddress(command.getClientIpAddress());
            
            log.info("[SAGA_CMD_HANDLER][{}] Creating payment request - BookingRef: {}, Amount: {}, PaymentMethod: {}", 
                    sagaId, createdBooking.getBookingReference(), createdBooking.getTotalAmount(), command.getPaymentMethod());
            
            String vnpayPaymentUrl;
            try {
                log.info("[SAGA_CMD_HANDLER][{}] Calling PaymentService.createVNPayPaymentUrl with userId: {}", sagaId, command.getUserId());
                vnpayPaymentUrl = paymentService.createVNPayPaymentUrl(paymentCreationRequest, command.getUserId());
                log.info("[SAGA_CMD_HANDLER][{}] PaymentService.createVNPayPaymentUrl returned: {}", sagaId, vnpayPaymentUrl);
            } catch (Exception e) {
                log.error("[SAGA_CMD_HANDLER][{}] Error calling PaymentService to create VNPAY URL: {}", sagaId,
                        e.getMessage(), e);
                handleLocalFailure(sagaId, SagaStep.FAILED_VNPAY_URL_GENERATION, "VNPAY_URL_SERVICE_ERROR", payload,
                        "Error from Payment Service: " + e.getMessage());
                return;
            }

            if (!StringUtils.hasText(vnpayPaymentUrl)) {
                log.error("[SAGA_CMD_HANDLER][{}] PaymentService returned empty VNPAY payment URL.", sagaId);
                handleLocalFailure(sagaId, SagaStep.FAILED_VNPAY_URL_GENERATION, "VNPAY_URL_EMPTY", payload,
                        "Payment Service returned an empty VNPAY URL.");
                return;
            }
            log.info("[SAGA_CMD_HANDLER][{}] VNPAY payment URL generated: {}", sagaId, vnpayPaymentUrl);

            payload.setVnpayPaymentUrl(vnpayPaymentUrl);
            payload.setPaymentDeadline(createdBooking.getPaymentDeadline());

            BookingCreatedPendingPaymentEvent successEvent = BookingCreatedPendingPaymentEvent.builder()
                    .sagaId(sagaId)
                    .bookingDatabaseId(createdBooking.getId())
                    .bookingReferenceDisplay(createdBooking.getBookingReference())
                    .vnpayPaymentUrl(vnpayPaymentUrl)
                    .paymentDeadline(createdBooking.getPaymentDeadline())
                    .totalAmount(createdBooking.getTotalAmount())
                    .build();

            updateSagaState(sagaId, SagaStep.PENDING_BOOKING_CREATED, payload, null);
            rabbitTemplate.convertAndSend(eventsExchange, RK_INTERNAL_BOOKING_CREATED_PENDING_PAYMENT_EVENT,
                    successEvent);
            log.info("[SAGA_CMD_HANDLER][{}] Published BookingCreatedPendingPaymentEvent with Booking DB ID: {}.",
                    sagaId, createdBooking.getId());

        } catch (ResourceNotFoundException | BadRequestException e) {
            log.error("[SAGA_CMD_HANDLER][{}] Business error creating pending booking entities: {}", sagaId,
                    e.getMessage(), e);
            handleLocalFailure(sagaId, SagaStep.FAILED_PROCESSING, "DB_BOOKING_CREATION_ERROR", payload,
                    e.getMessage());
        } catch (Exception e) {
            log.error(
                    "[SAGA_CMD_HANDLER][{}] Unexpected error during pending booking creation or VNPAY URL generation: {}",
                    sagaId, e.getMessage(), e);
            handleLocalFailure(sagaId, SagaStep.FAILED_PROCESSING, "UNEXPECTED_ERROR_IN_HANDLER", payload,
                    e.getMessage());
        }
    }

    private SagaState loadSagaState(UUID sagaId) {
        return sagaStateRepository.findById(sagaId)
                .orElseThrow(() -> new SagaProcessingException(
                        "SagaState not found for id: " + sagaId + " in PendingBookingCreationHandler"));
    }

    private void updateSagaState(UUID sagaId, SagaStep currentStep,
                                 CreateBookingSagaOrchestrator.CreateBookingSagaPayload payload, String errorMessage) {
        SagaState sagaState = sagaStateRepository.findById(sagaId)
                .orElseGet(() -> SagaState.builder().sagaId(sagaId).createdAt(LocalDateTime.now()).build());

        sagaState.setCurrentStep(currentStep);
        sagaState.setPayloadJson(serializePayload(payload));
        sagaState.setErrorMessage(errorMessage);
        sagaState.setUpdatedAt(LocalDateTime.now());
        sagaStateRepository.save(sagaState);
    }

    private void handleLocalFailure(UUID sagaId, SagaStep failedStep, String reasonCode,
                                    CreateBookingSagaOrchestrator.CreateBookingSagaPayload payload, String detailedMessage) {
        log.error("[SAGA_HANDLER_FAIL][{}] Step failed: {}. Reason: {}, Details: {}", sagaId, failedStep, reasonCode,
                detailedMessage);
        updateSagaState(sagaId, failedStep, payload, reasonCode + ": " + detailedMessage);

        String userFriendlyBookingRef = (payload != null && payload.getUserFriendlyBookingReference() != null)
                ? payload.getUserFriendlyBookingReference()
                : "SAGA_ID_" + sagaId.toString();
        bookingService.failSagaInitiationBeforePaymentUrl(sagaId, userFriendlyBookingRef,
                reasonCode + ": " + detailedMessage);
    }

    private String serializePayload(CreateBookingSagaOrchestrator.CreateBookingSagaPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("Error serializing saga payload in handler for sagaId (userId if available): {}",
                    (payload != null && payload.getUserId() != null) ? payload.getUserId() : "UNKNOWN_SAGA", e);
            throw new SagaProcessingException("Error serializing saga payload", e);
        }
    }

    private CreateBookingSagaOrchestrator.CreateBookingSagaPayload deserializePayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            log.warn("Attempted to deserialize null or blank payload JSON in handler.");
            return CreateBookingSagaOrchestrator.CreateBookingSagaPayload.builder().build();
        }
        try {
            return objectMapper.readValue(payloadJson, CreateBookingSagaOrchestrator.CreateBookingSagaPayload.class);
        } catch (JsonProcessingException e) {
            log.error("Error deserializing saga payload in handler: {}", payloadJson, e);
            throw new SagaProcessingException("Error deserializing saga payload", e);
        }
    }
}