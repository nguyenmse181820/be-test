package com.boeing.bookingservice.saga.orchestrator;

import com.boeing.bookingservice.dto.request.CreateBookingRequestDTO;
import com.boeing.bookingservice.dto.request.PassengerInfoDTO;
import com.boeing.bookingservice.exception.SagaProcessingException;
import com.boeing.bookingservice.integration.fs.FlightClient;
import com.boeing.bookingservice.integration.fs.dto.*;
import com.boeing.bookingservice.saga.SagaStep;
import com.boeing.bookingservice.saga.command.CreatePendingBookingInternalCommand;
import com.boeing.bookingservice.saga.event.BookingCreatedPendingPaymentEvent;
import com.boeing.bookingservice.saga.event.CreateBookingSagaFailedEvent;
import com.boeing.bookingservice.saga.event.FareAvailabilityCheckedEvent;
import com.boeing.bookingservice.saga.event.SeatAvailabilityCheckedEvent;
import com.boeing.bookingservice.saga.state.SagaState;
import com.boeing.bookingservice.saga.state.SagaStateRepository;
import com.boeing.bookingservice.service.BookingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class CreateBookingSagaOrchestrator {
    private final RabbitTemplate rabbitTemplate;
    private final FlightClient flightClient;
    private final SagaStateRepository sagaStateRepository;
    private final BookingService bookingService;
    private final ObjectMapper objectMapper;
    @Value("${app.rabbitmq.routingKeys.internalFareCheckedEvent}")
    private String RK_INTERNAL_FARE_CHECKED_EVENT;
    @Value("${app.rabbitmq.routingKeys.internalSeatAvailabilityCheckedEvent}")
    private String RK_INTERNAL_SEAT_AVAILABILITY_CHECKED_EVENT;
    @Value("${app.rabbitmq.routingKeys.internalCreatePendingBookingCmdKey}")
    private String RK_INTERNAL_CREATE_PENDING_BOOKING_CMD;
    @Value("${app.rabbitmq.routingKeys.sagaCreateBookingFailedEvent}")
    private String RK_SAGA_CREATE_BOOKING_FAILED_EVENT;
    @Value("${app.rabbitmq.routingKeys.internalBookingCreatedEvent}")
    private String RK_INTERNAL_BOOKING_CREATED_PENDING_PAYMENT_EVENT;
    @Value("${app.rabbitmq.exchanges.events}")
    private String eventsExchange;
    @Value("${app.rabbitmq.exchanges.commands}")
    private String commandsExchange;

    public CreateBookingSagaOrchestrator(
            RabbitTemplate rabbitTemplate,
            FlightClient flightClient,
            SagaStateRepository sagaStateRepository,
            ObjectMapper objectMapper,
            @Lazy BookingService bookingService) {
        this.rabbitTemplate = rabbitTemplate;
        this.flightClient = flightClient;
        this.sagaStateRepository = sagaStateRepository;
        this.objectMapper = objectMapper;
        this.bookingService = bookingService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void startSaga(CreateBookingRequestDTO request, UUID userId, UUID sagaId, String bookingReferenceForUser,
                          String clientIpAddress) {
        log.info("[SAGA_START][{}] User: {}, Flight: {}, BookingRefForUser: {}, IP: {}",
                sagaId, userId, request.getFlightId(), bookingReferenceForUser, clientIpAddress);

        CreateBookingSagaPayload payload = CreateBookingSagaPayload.builder()
                .initialRequest(request)
                .userId(userId)
                .userFriendlyBookingReference(bookingReferenceForUser)
                .clientIpAddress(clientIpAddress)
                .build();
        saveSagaState(sagaId, SagaStep.STARTED, payload, null);

        if (request.getPassengers() == null || request.getPassengers().isEmpty()) {
            handleSagaFailure(sagaId, SagaStep.FAILED_PROCESSING, "No passengers in request.", payload);
            bookingService.failSagaInitiationBeforePaymentUrl(sagaId, bookingReferenceForUser,
                    "No passengers provided.");
            return;
        }
        int requestedPassengerCount = request.getPassengers().size();        try {
            log.info("[SAGA_STEP][{}] Checking fare availability for flight: {}, fare: {}, count: {}",
                    sagaId, request.getFlightId(), request.getSelectedFareName(), requestedPassengerCount);

            // Use getFlightDetails instead of checkFareAvailability
            FsFlightWithFareDetailsDTO flightDetails = flightClient.getFlightDetails(request.getFlightId());
            
            // Check if the requested fare exists and has enough seats
            boolean fareIsActuallyAvailable = flightDetails.getAvailableFares().stream()
                    .anyMatch(fare -> fare.getName().equalsIgnoreCase(request.getSelectedFareName()) 
                            && fare.getSeatsAvailableForFare() != null 
                            && fare.getSeatsAvailableForFare() >= requestedPassengerCount);            log.info("[SAGA_STEP][{}] Fare availability check result: {}", sagaId, fareIsActuallyAvailable);

            // Find the requested fare details
            FsDetailedFareDTO requestedFare = flightDetails.getAvailableFares().stream()
                    .filter(fare -> fare.getName().equalsIgnoreCase(request.getSelectedFareName()))
                    .findFirst()
                    .orElse(null);

            FareAvailabilityCheckedEvent fareEvent = FareAvailabilityCheckedEvent.builder()
                    .sagaId(sagaId)
                    .fareAvailable(fareIsActuallyAvailable)
                    .requestedFareName(request.getSelectedFareName())
                    .requestedCount(requestedPassengerCount)
                    .actualAvailableCount(requestedFare != null ? requestedFare.getSeatsAvailableForFare() : 0)
                    .pricePerPassengerForFare(requestedFare != null ? requestedFare.getPrice() : null)
                    .failureReason(!fareIsActuallyAvailable
                            ? "Not enough seats available for the selected fare or fare not found."
                            : null)
                    .build();

            updateSagaState(sagaId, SagaStep.AWAITING_FARE_AVAILABILITY_RESPONSE, payload);
            rabbitTemplate.convertAndSend(eventsExchange, RK_INTERNAL_FARE_CHECKED_EVENT, fareEvent);
            log.info("[SAGA_STEP][{}] Published internal FareAvailabilityCheckedEvent.", sagaId);        } catch (Exception e) {
            log.error("[SAGA_ERROR][{}] Failed to get flight details for fare availability check: {}", sagaId, e.getMessage(), e);
            handleSagaFailure(sagaId, SagaStep.FAILED_FETCH_FLIGHT_DETAILS,
                    "FIS_CALL_FLIGHT_DETAILS_FAILED: " + e.getMessage(), payload);
            bookingService.failSagaInitiationBeforePaymentUrl(sagaId, bookingReferenceForUser,
                    "Failed to get flight details from Flight Service.");
        }
    }

    @RabbitListener(queues = "${app.rabbitmq.queues.internalFareChecked}")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleFareAvailabilityChecked(FareAvailabilityCheckedEvent event) {
        UUID sagaId = event.getSagaId();
        log.info("[SAGA_EVENT][{}] Received FareAvailabilityCheckedEvent. Fare '{}' available for {} passengers: {}",
                sagaId, event.getRequestedFareName(), event.getRequestedCount(), event.isFareAvailable());

        SagaState sagaState = loadSagaState(sagaId);
        CreateBookingSagaPayload payload = deserializePayload(sagaState.getPayloadJson());
        payload.setFareAvailableForCount(event.isFareAvailable());

        if (!event.isFareAvailable()) {
            handleSagaFailure(sagaId, SagaStep.FAILED_FARE_UNAVAILABLE, event.getFailureReason(), payload);
            bookingService.failSagaInitiationBeforePaymentUrl(sagaId, payload.getUserFriendlyBookingReference(),
                    event.getFailureReason() != null ? event.getFailureReason()
                            : "Selected fare not available for the requested count.");
            return;
        }

        try {
            FsFlightWithFareDetailsDTO flightDetails = flightClient
                    .getFlightDetails(payload.getInitialRequest().getFlightId());
            payload.setFlightDetailsSnapshot(flightDetails);

            Double bookingAmountBeforeDiscount = calculateBookingAmount(
                    flightDetails,
                    payload.getInitialRequest().getPassengers().size(),
                    payload.getInitialRequest().getSelectedFareName());
            payload.setTotalAmountBeforeDiscount(bookingAmountBeforeDiscount);
            log.info("[SAGA_INFO][{}] Calculated totalAmountBeforeDiscount: {}", sagaId, bookingAmountBeforeDiscount);            saveSagaState(sagaId, SagaStep.FARE_AVAILABILITY_CHECKED, payload, null);

            // Check if user provided seat selections
            if (payload.getInitialRequest().getSeatSelections() != null && 
                !payload.getInitialRequest().getSeatSelections().isEmpty()) {
                log.info("[SAGA_STEP][{}] User provided seat selections, checking seat availability.", sagaId);
                checkSeatAvailability(sagaId, payload);
            } else {
                log.info("[SAGA_STEP][{}] No seat selections provided, proceeding to create pending booking.", sagaId);
                sendCreatePendingBookingInternalCommand(sagaId, payload);
            }

        } catch (SagaProcessingException spe) {
            log.error(
                    "[SAGA_ERROR][{}] SagaProcessingException during price calculation or flight details fetching: {}",
                    sagaId, spe.getMessage(), spe);
            handleSagaFailure(sagaId, SagaStep.FAILED_PROCESSING, spe.getMessage(), payload);
            bookingService.failSagaInitiationBeforePaymentUrl(sagaId, payload.getUserFriendlyBookingReference(),
                    "Error processing booking details or calculating price.");
        } catch (Exception e) {
            log.error("[SAGA_ERROR][{}] Failed during flight details fetching: {}", sagaId, e.getMessage(), e);
            handleSagaFailure(sagaId, SagaStep.FAILED_FETCH_FLIGHT_DETAILS,
                    "FIS_CALL_FLIGHT_DETAILS_FAILED: " + e.getMessage(), payload);
            bookingService.failSagaInitiationBeforePaymentUrl(sagaId, payload.getUserFriendlyBookingReference(),
                    "Error processing flight details.");
        }
    }

    private void sendCreatePendingBookingInternalCommand(UUID sagaId, CreateBookingSagaPayload currentPayload) {

        Double totalAmountBeforeDiscount = currentPayload.getTotalAmountBeforeDiscount();
        if (totalAmountBeforeDiscount == null) {
            log.error("[SAGA_ERROR][{}] totalAmountBeforeDiscount is null in payload. It should have been calculated.",
                    sagaId);
            handleSagaFailure(sagaId, SagaStep.FAILED_PROCESSING, "Internal error: Original booking amount missing.",
                    currentPayload);
            bookingService.failSagaInitiationBeforePaymentUrl(sagaId, currentPayload.getUserFriendlyBookingReference(),
                    "Internal error calculating booking amount.");
            return;
        }

        Double finalTotalAmount = totalAmountBeforeDiscount;
        currentPayload.setFinalTotalAmount(finalTotalAmount);

        LocalDateTime paymentDeadline = LocalDateTime.now().plusMinutes(10);
        currentPayload.setPaymentDeadline(paymentDeadline);

        List<PassengerInfoDTO> passengersForCommand = currentPayload.getInitialRequest().getPassengers();

        FsFlightWithFareDetailsDTO flightDetailsSnapshot = currentPayload.getFlightDetailsSnapshot();
        if (flightDetailsSnapshot == null) {
            log.error("[SAGA_ERROR][{}] Flight details snapshot missing in payload.", sagaId);
            handleSagaFailure(sagaId, SagaStep.FAILED_PROCESSING, "Internal error: Flight details snapshot missing.",
                    currentPayload);
            bookingService.failSagaInitiationBeforePaymentUrl(sagaId, currentPayload.getUserFriendlyBookingReference(),
                    "Internal error: Flight details missing.");
            return;
        }

        CreatePendingBookingInternalCommand createCmd = CreatePendingBookingInternalCommand.builder()
                .sagaId(sagaId)
                .bookingReference(currentPayload.getUserFriendlyBookingReference())
                .flightId(currentPayload.getInitialRequest().getFlightId())
                .passengersInfo(passengersForCommand)
                .selectedFareName(currentPayload.getInitialRequest().getSelectedFareName())
                .seatSelections(currentPayload.getInitialRequest().getSeatSelections())
                .userId(currentPayload.getUserId())
                .totalAmount(finalTotalAmount)
                .paymentDeadline(paymentDeadline)
                .paymentMethod(currentPayload.getInitialRequest().getPaymentMethod())
                .snapshotFlightCode(flightDetailsSnapshot.getFlightCode())
                .snapshotOriginAirportCode(flightDetailsSnapshot.getOriginAirport().getCode())
                .snapshotDestinationAirportCode(flightDetailsSnapshot.getDestinationAirport().getCode())
                .snapshotDepartureTime(flightDetailsSnapshot.getDepartureTime())
                .snapshotArrivalTime(flightDetailsSnapshot.getEstimatedArrivalTime())
                .clientIpAddress(currentPayload.getClientIpAddress())
                .build();

        updateSagaState(sagaId, SagaStep.AWAITING_PENDING_BOOKING_CREATION, currentPayload);
        rabbitTemplate.convertAndSend(commandsExchange, RK_INTERNAL_CREATE_PENDING_BOOKING_CMD, createCmd);
        log.info("[SAGA_STEP][{}] Published CreatePendingBookingInternalCommand. UserBookingRef: {}, Final Amount: {}",
                sagaId, currentPayload.getUserFriendlyBookingReference(), finalTotalAmount);
    }

    @RabbitListener(queues = "${app.rabbitmq.queues.internalBookingCreatedPendingPayment}")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleBookingCreatedPendingPayment(BookingCreatedPendingPaymentEvent event) {
        UUID sagaId = event.sagaId();
        log.info(
                "[SAGA_EVENT][{}] Received BookingCreatedPendingPaymentEvent. Booking DB ID: {}, Payment URL: {}, Deadline: {}",
                sagaId, event.bookingDatabaseId(), event.vnpayPaymentUrl(), event.paymentDeadline());

        SagaState sagaState = loadSagaState(sagaId);
        CreateBookingSagaPayload payload = deserializePayload(sagaState.getPayloadJson());

        payload.setVnpayPaymentUrl(event.vnpayPaymentUrl());
        payload.setPaymentDeadline(event.paymentDeadline());

        saveSagaState(sagaId, SagaStep.AWAITING_PAYMENT_IPN, payload, null);

        bookingService.completeSagaInitiationWithPaymentUrl(
                sagaId,
                payload.getUserFriendlyBookingReference(),
                payload.getFinalTotalAmount(),
                payload.getVnpayPaymentUrl(),
                payload.getPaymentDeadline());
        log.info("[SAGA_INFO][{}] Saga is now AWAITING_PAYMENT_IPN. Client has been notified. UserBookingRef: {}",
                sagaId, payload.getUserFriendlyBookingReference());
    }

    public void cancelSaga(UUID sagaId, String reason) {
        try {
            log.info("[SAGA_CANCEL][{}] Cancelling saga with reason: {}", sagaId, reason);
            
            SagaState sagaState = sagaStateRepository.findById(sagaId).orElse(null);
            if (sagaState != null) {
                sagaState.setCurrentStep(SagaStep.CANCELLED_CLIENT_TIMEOUT);
                sagaState.setUpdatedAt(LocalDateTime.now());
                sagaStateRepository.save(sagaState);
                
                log.info("[SAGA_CANCEL][{}] Saga cancelled successfully", sagaId);
            } else {
                log.warn("[SAGA_CANCEL][{}] Saga state not found for cancellation", sagaId);
            }
            
        } catch (Exception e) {
            log.error("[SAGA_CANCEL][{}] Error cancelling saga: {}", sagaId, e.getMessage(), e);
        }
    }

    // --- Helper Methods ---
    private SagaState loadSagaState(UUID sagaId) {
        return sagaStateRepository.findById(sagaId)
                .orElseThrow(() -> {
                    log.error("SagaState not found for id: {}", sagaId);
                    // TODO: Cần lấy userFriendlyBookingReference từ đâu đó nếu sagaId là UUID thuần
                    // túy
                    return new SagaProcessingException("SagaState not found for id: " + sagaId + ". Cannot proceed.");
                });
    }

    private void saveSagaState(UUID sagaId, SagaStep currentStep, CreateBookingSagaPayload payload,
                               String errorMessage) {
        SagaState sagaState = sagaStateRepository.findById(sagaId)
                .orElseGet(() -> SagaState.builder().sagaId(sagaId).createdAt(LocalDateTime.now()).build());

        sagaState.setCurrentStep(currentStep);
        sagaState.setPayloadJson(serializePayload(payload));
        sagaState.setErrorMessage(errorMessage);
        sagaState.setUpdatedAt(LocalDateTime.now());
        sagaStateRepository.save(sagaState);
    }

    private void updateSagaState(UUID sagaId, SagaStep nextStep, CreateBookingSagaPayload currentPayload) {
        saveSagaState(sagaId, nextStep, currentPayload, null);
    }

    private void handleSagaFailure(UUID sagaId, SagaStep failedStep, String reason,
                                   CreateBookingSagaPayload payloadContext) {
        log.error("[SAGA_HANDLE_FAIL][{}] Saga failed at step: {}. Reason: {}", sagaId, failedStep, reason);
        saveSagaState(sagaId, failedStep, payloadContext, reason);

        // Extract flight and fare info for compensation
        UUID flightId = null;
        String fareName = null;
        String bookingReference = null;

        if (payloadContext != null && payloadContext.getInitialRequest() != null) {
            flightId = payloadContext.getInitialRequest().getFlightId();
            fareName = payloadContext.getInitialRequest().getSelectedFareName();
            bookingReference = payloadContext.getUserFriendlyBookingReference();
        }
        CreateBookingSagaFailedEvent failEvent = new CreateBookingSagaFailedEvent(
                sagaId,
                bookingReference,
                failedStep.name(),
                reason,
                flightId,
                fareName);

        rabbitTemplate.convertAndSend(eventsExchange, RK_SAGA_CREATE_BOOKING_FAILED_EVENT, failEvent);
    }

    private String serializePayload(CreateBookingSagaPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("Error serializing saga payload for sagaId: {}",
                    payload != null && payload.getInitialRequest() != null ? payload.getInitialRequest().getFlightId()
                            : "UNKNOWN",
                    e);
            throw new SagaProcessingException("Error serializing saga payload", e);
        }
    }

    private CreateBookingSagaPayload deserializePayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            log.warn("Attempted to deserialize null or blank payload JSON. Returning new payload.");
            return CreateBookingSagaPayload.builder().build();
        }
        try {
            return objectMapper.readValue(payloadJson, CreateBookingSagaPayload.class);
        } catch (JsonProcessingException e) {
            log.error("Error deserializing saga payload: {}", payloadJson, e);
            throw new SagaProcessingException("Error deserializing saga payload", e);
        }
    }

    private Double calculateBookingAmount(
            FsFlightWithFareDetailsDTO flightDetails,
            int passengerCount,
            String selectedFareName) {

        if (flightDetails == null || flightDetails.getAvailableFares() == null
                || flightDetails.getAvailableFares().isEmpty()) {
            throw new SagaProcessingException("Flight details or available fares are missing for price calculation.");
        }
        if (passengerCount <= 0) {
            return 0.0;
        }

        Optional<FsDetailedFareDTO> selectedFareOptional = flightDetails.getAvailableFares().stream()
                .filter(fare -> fare.getName() != null && fare.getName().equalsIgnoreCase(selectedFareName))
                .findFirst();

        if (selectedFareOptional.isPresent()) {
            FsDetailedFareDTO fare = selectedFareOptional.get();
            if (fare.getPrice() != null && fare.getPrice() >= 0.0) {
                return fare.getPrice() * passengerCount;
            } else {
                throw new SagaProcessingException("Invalid price for selected fare: " + fare.getName());
            }
        } else {
            throw new SagaProcessingException(
                    "Selected fare '" + selectedFareName + "' is not available for this flight.");
        }
    }

    public void confirmFareForSuccessfulBooking(UUID sagaId, UUID flightId, String fareName, String bookingReference, int passengerCount) {
        try {
            FsConfirmFareSaleRequestDTO request = FsConfirmFareSaleRequestDTO.builder()
                    .bookingReference(bookingReference)
                    .soldCount(passengerCount)
                    .build();

            FsConfirmFareSaleResponseDTO response = flightClient.confirmFareSale(flightId, fareName, request);

            boolean fareConfirmed = response != null && response.isSuccess();

            if (fareConfirmed) {
                log.info("[SAGA_FARE_CONFIRMED][{}] Successfully confirmed fare hold for booking: {}",
                        sagaId, bookingReference);
            } else {
                log.warn("[SAGA_FARE_CONFIRM_FAILED][{}] Failed to confirm fare hold for booking: {}: {}",
                        sagaId, bookingReference, response != null ? response.getFailureReason() : "No response");
            }
        } catch (Exception e) {
            log.error("[SAGA_FARE_CONFIRM_ERROR][{}] Error confirming fare hold for booking: {}",
                    sagaId, bookingReference, e);
        }
    }

    private void checkSeatAvailability(UUID sagaId, CreateBookingSagaPayload payload) {
        try {
            List<String> requestedSeatCodes = payload.getInitialRequest().getSeatSelections()
                    .stream()
                    .map(seatSelection -> seatSelection.getSeatCode())
                    .toList();

            log.info("[SAGA_STEP][{}] Checking availability for {} seats: {}", 
                    sagaId, requestedSeatCodes.size(), requestedSeatCodes);
            
            // Log the exact request details
            UUID flightId = payload.getInitialRequest().getFlightId();
            log.info("[SAGA_DEBUG][{}] About to call Flight Service - FlightId: {}, SeatCodes: {}", 
                    sagaId, flightId, requestedSeatCodes);

            updateSagaState(sagaId, SagaStep.AWAITING_SEAT_AVAILABILITY_RESPONSE, payload);

            FsSeatsAvailabilityResponseDTO response = flightClient.checkSeatsAvailability(flightId, requestedSeatCodes);

            SeatAvailabilityCheckedEvent event = SeatAvailabilityCheckedEvent.builder()
                    .sagaId(sagaId)
                    .flightId(payload.getInitialRequest().getFlightId())
                    .requestedSeatCodes(requestedSeatCodes)
                    .allSeatsAvailable(response.isAllRequestedSeatsAvailable())
                    .unavailableSeats(response.isAllRequestedSeatsAvailable() ? null : 
                            response.getSeatStatuses().stream()
                                    .filter(status -> !status.isAvailable())
                                    .map(status -> status.getSeatCode())
                                    .toList())
                    .failureReason(response.isAllRequestedSeatsAvailable() ? null : 
                            "Some requested seats are not available")
                    .build();

            rabbitTemplate.convertAndSend(eventsExchange, RK_INTERNAL_SEAT_AVAILABILITY_CHECKED_EVENT, event);
            log.info("[SAGA_STEP][{}] Published SeatAvailabilityCheckedEvent.", sagaId);

        } catch (feign.FeignException.InternalServerError e) {
            log.error("[SAGA_ERROR][{}] Flight Service returned 500 error for seat check. FlightId: {}, SeatCodes: {}, Response: {}", 
                    sagaId, payload.getInitialRequest().getFlightId(), 
                    payload.getInitialRequest().getSeatSelections().stream()
                            .map(sel -> sel.getSeatCode()).toList(),
                    e.contentUTF8(), e);
            
            try {
                saveSagaState(sagaId, SagaStep.SEAT_AVAILABILITY_CHECKED, payload, 
                        "Seat check bypassed due to Flight Service error: " + e.getMessage());
                sendCreatePendingBookingInternalCommand(sagaId, payload);
                return;
            } catch (Exception fallbackError) {
                log.error("[SAGA_ERROR][{}] Fallback also failed: {}", sagaId, fallbackError.getMessage(), fallbackError);
            }
            
            handleSagaFailure(sagaId, SagaStep.FAILED_PROCESSING, 
                    "SEAT_AVAILABILITY_CHECK_FAILED: " + e.getMessage(), payload);
            bookingService.failSagaInitiationBeforePaymentUrl(sagaId, payload.getUserFriendlyBookingReference(),
                    "Failed to check seat availability with Flight Service.");
        } catch (Exception e) {
            log.error("[SAGA_ERROR][{}] Failed to check seat availability: {}", sagaId, e.getMessage(), e);
            handleSagaFailure(sagaId, SagaStep.FAILED_PROCESSING, 
                    "SEAT_AVAILABILITY_CHECK_FAILED: " + e.getMessage(), payload);
            bookingService.failSagaInitiationBeforePaymentUrl(sagaId, payload.getUserFriendlyBookingReference(),
                    "Failed to check seat availability with Flight Service.");
        }
    }

    @RabbitListener(queues = "${app.rabbitmq.queues.internalSeatAvailabilityChecked}")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleSeatAvailabilityChecked(SeatAvailabilityCheckedEvent event) {
        UUID sagaId = event.getSagaId();
        log.info("[SAGA_EVENT][{}] Received SeatAvailabilityCheckedEvent. All seats available: {}", 
                sagaId, event.isAllSeatsAvailable());

        SagaState sagaState = loadSagaState(sagaId);
        CreateBookingSagaPayload payload = deserializePayload(sagaState.getPayloadJson());

        if (!event.isAllSeatsAvailable()) {
            String failureMsg = String.format("Seats not available: %s", event.getUnavailableSeats());
            handleSagaFailure(sagaId, SagaStep.FAILED_SEAT_UNAVAILABLE, failureMsg, payload);
            bookingService.failSagaInitiationBeforePaymentUrl(sagaId, payload.getUserFriendlyBookingReference(),
                    "Some of the selected seats are no longer available. Please select different seats.");
            return;
        }

        try {
            saveSagaState(sagaId, SagaStep.SEAT_AVAILABILITY_CHECKED, payload, null);
            log.info("[SAGA_STEP][{}] All seats available, proceeding to create pending booking.", sagaId);
            sendCreatePendingBookingInternalCommand(sagaId, payload);

        } catch (Exception e) {
            log.error("[SAGA_ERROR][{}] Error after seat availability check: {}", sagaId, e.getMessage(), e);
            handleSagaFailure(sagaId, SagaStep.FAILED_PROCESSING, 
                    "Error after seat availability check: " + e.getMessage(), payload);
            bookingService.failSagaInitiationBeforePaymentUrl(sagaId, payload.getUserFriendlyBookingReference(),
                    "Error processing seat availability check.");
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateBookingSagaPayload {
        private CreateBookingRequestDTO initialRequest;
        private UUID userId;
        private String userFriendlyBookingReference;
        private String clientIpAddress;
        private boolean fareAvailableForCount;
        private FsFlightWithFareDetailsDTO flightDetailsSnapshot;
        private Double totalAmountBeforeDiscount;
        private Double finalTotalAmount;
        private String vnpayPaymentUrl;
        private LocalDateTime paymentDeadline;
    }

}
