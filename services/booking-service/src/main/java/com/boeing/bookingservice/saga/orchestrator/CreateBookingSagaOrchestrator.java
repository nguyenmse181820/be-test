package com.boeing.bookingservice.saga.orchestrator;

import com.boeing.bookingservice.dto.request.CreateBookingRequestDTO;
import com.boeing.bookingservice.dto.request.PassengerInfoDTO;
import com.boeing.bookingservice.exception.SagaProcessingException;
import com.boeing.bookingservice.integration.fs.FlightClient;
import com.boeing.bookingservice.integration.fs.dto.*;
import com.boeing.bookingservice.saga.SagaStep;
import com.boeing.bookingservice.saga.command.CreatePendingBookingInternalCommand;
import com.boeing.bookingservice.saga.command.CreatePendingMultiSegmentBookingInternalCommand;
import com.boeing.bookingservice.saga.event.BookingCreatedPendingPaymentEvent;
import com.boeing.bookingservice.saga.event.CreateBookingSagaFailedEvent;
import com.boeing.bookingservice.saga.event.FareAvailabilityCheckedEvent;
import com.boeing.bookingservice.saga.event.SeatAvailabilityCheckedEvent;
import com.boeing.bookingservice.saga.state.SagaState;
import com.boeing.bookingservice.saga.state.SagaStateRepository;
import com.boeing.bookingservice.service.BookingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
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
import java.util.*;
import java.util.concurrent.TimeoutException;

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
    @Value("${app.rabbitmq.routingKeys.internalCreateMultiSegmentPendingBookingCmdKey}")
    private String RK_INTERNAL_CREATE_MULTI_SEGMENT_PENDING_BOOKING_CMD;
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
    @CircuitBreaker(name = "flightServiceCircuitBreaker", fallbackMethod = "startSagaFallback")
    @Retry(name = "flightServiceRetry")
    public void startSaga(CreateBookingRequestDTO request, UUID userId, UUID sagaId, String bookingReferenceForUser,
                          String clientIpAddress) {
        // Determine if this is a multi-segment booking - must have more than 1 flight in flightIds
        boolean isMultiSegment = request.getFlightIds() != null && request.getFlightIds().size() > 1;

        log.info("[SAGA_DEBUG][{}] Received booking request: flightIds={}, isMultiSegment={}, selectedFareName='{}'",
                sagaId, request.getFlightIds(), isMultiSegment, request.getSelectedFareName());

        log.info("[SAGA_START][{}] User: {}, Flight(s): {}, BookingRefForUser: {}, IP: {}, MultiSegment: {}",
                sagaId, userId, isMultiSegment ? request.getFlightIds() : request.getFlightId(),
                bookingReferenceForUser, clientIpAddress, isMultiSegment);

        CreateBookingSagaPayload payload = CreateBookingSagaPayload.builder()
                .initialRequest(request)
                .userId(userId)
                .userFriendlyBookingReference(bookingReferenceForUser)
                .clientIpAddress(clientIpAddress)
                .build();
        saveSagaState(sagaId, SagaStep.STARTED, payload, null);

        // Basic validation
        if (request.getPassengers() == null || request.getPassengers().isEmpty()) {
            log.error("[SAGA_VALIDATION][{}] No passengers in booking request", sagaId);
            handleSagaFailure(sagaId, SagaStep.FAILED_PROCESSING, "No passengers in request.", payload);
            bookingService.failSagaInitiationBeforePaymentUrl(sagaId, bookingReferenceForUser,
                    "No passengers provided.");
            return;
        }

        // Validate flight information based on booking type
        if (isMultiSegment) {
            if (request.getFlightIds() == null || request.getFlightIds().isEmpty()) {
                log.error("[SAGA_VALIDATION][{}] No flight IDs in multi-segment booking request", sagaId);
                handleSagaFailure(sagaId, SagaStep.FAILED_PROCESSING, "No flight IDs in multi-segment request.", payload);
                bookingService.failSagaInitiationBeforePaymentUrl(sagaId, bookingReferenceForUser,
                        "No flights selected for multi-segment booking.");
                return;
            }
        } else {
            if (request.getFlightId() == null) {
                log.error("[SAGA_VALIDATION][{}] No flight ID in single-segment booking request", sagaId);
                handleSagaFailure(sagaId, SagaStep.FAILED_PROCESSING, "No flight ID in single-segment request.", payload);
                bookingService.failSagaInitiationBeforePaymentUrl(sagaId, bookingReferenceForUser,
                        "No flight selected.");
                return;
            }
        }

        if (request.getSelectedFareName() == null || request.getSelectedFareName().isBlank()) {
            log.error("[SAGA_VALIDATION][{}] No fare name in booking request", sagaId);
            handleSagaFailure(sagaId, SagaStep.FAILED_PROCESSING, "No fare name in request.", payload);
            bookingService.failSagaInitiationBeforePaymentUrl(sagaId, bookingReferenceForUser,
                    "No fare selected.");
            return;
        }

        // Route to appropriate processing based on booking type
        if (isMultiSegment) {
            processMultiSegmentBooking(request, userId, sagaId, bookingReferenceForUser, clientIpAddress, payload);
        } else {
            processSingleSegmentBooking(request, userId, sagaId, bookingReferenceForUser, clientIpAddress, payload);
        }
    }

    private void processSingleSegmentBooking(CreateBookingRequestDTO request, UUID userId, UUID sagaId,
                                             String bookingReferenceForUser, String clientIpAddress,
                                             CreateBookingSagaPayload payload) {
        int requestedPassengerCount = request.getPassengers().size();
        try {
            log.info("[SAGA_STEP][{}] Checking fare availability for flight: {}, fare: {}, count: {}",
                    sagaId, request.getFlightId(), request.getSelectedFareName(), requestedPassengerCount);

            // Record start time for monitoring
            long startTime = System.currentTimeMillis();

            // Enhanced error handling around API call
            FsFlightWithFareDetailsDTO flightDetails = null;
            try {
                flightDetails = flightClient.getFlightDetails(request.getFlightId());
            } catch (feign.FeignException.InternalServerError e) {
                log.error("[SAGA_ERROR][{}] Flight Service returned 500 error: {}", sagaId, e.contentUTF8(), e);
                throw new SagaProcessingException("Flight service internal error: " + e.getMessage());
            } catch (feign.FeignException.NotFound e) {
                log.error("[SAGA_ERROR][{}] Flight ID not found: {}", sagaId, request.getFlightId(), e);
                throw new SagaProcessingException("Flight not found: " + request.getFlightId());
            } catch (feign.RetryableException e) {
                log.error("[SAGA_ERROR][{}] Timeout connecting to Flight Service: {}", sagaId, e.getMessage(), e);
                throw new SagaProcessingException("Flight service timeout: " + e.getMessage());
            }

            long endTime = System.currentTimeMillis();
            log.info("[SAGA_STEP][{}] Flight details retrieved in {} ms", sagaId, (endTime - startTime));

            // Comprehensive validation of response
            if (flightDetails == null) {
                log.error("[SAGA_ERROR][{}] Null response received from Flight Service", sagaId);
                throw new SagaProcessingException("Null response received from Flight Service");
            }

            if (flightDetails.getAvailableFares() == null || flightDetails.getAvailableFares().isEmpty()) {
                log.error("[SAGA_ERROR][{}] Flight {} has no available fares", sagaId, request.getFlightId());
                throw new SagaProcessingException("No available fares for flight " + request.getFlightId());
            }

            // Log available fares for debugging
            log.info("[SAGA_DEBUG][{}] Available fares for flight {}: {}", sagaId, request.getFlightId(),
                    flightDetails.getAvailableFares().stream()
                            .map(fare -> fare.getName() + " (seats: " + calculateAvailableSeats(fare) + ")")
                            .toList());
            log.info("[SAGA_DEBUG][{}] Requested fare name: '{}'", sagaId, request.getSelectedFareName());

            // Check if the requested fare exists and has enough seats
            boolean fareIsActuallyAvailable = flightDetails.getAvailableFares().stream()
                    .anyMatch(fare -> fare.getName() != null &&
                            isFareNameMatch(fare.getName(), request.getSelectedFareName()) &&
                            calculateAvailableSeats(fare) >= requestedPassengerCount);

            log.info("[SAGA_STEP][{}] Fare availability check result: {}", sagaId, fareIsActuallyAvailable);

            // Find the requested fare details with more careful null checking
            FsDetailedFareDTO requestedFare = flightDetails.getAvailableFares().stream()
                    .filter(fare -> fare.getName() != null &&
                            isFareNameMatch(fare.getName(), request.getSelectedFareName()))
                    .findFirst()
                    .orElse(null);

            // Enhanced logging with detailed fare information
            if (requestedFare != null) {
                log.info("[SAGA_STEP][{}] Fare details - name: {}, price: {}, availableSeats: {}, benefits: {}",
                        sagaId, requestedFare.getName(), requestedFare.getPrice(),
                        calculateAvailableSeats(requestedFare),
                        requestedFare.getBenefits() != null ? requestedFare.getBenefits().size() : 0);
            } else {
                log.warn("[SAGA_STEP][{}] Requested fare {} not found in flight details",
                        sagaId, request.getSelectedFareName());

                // Log all available fares for debugging
                log.debug("[SAGA_DEBUG][{}] Available fares:", sagaId);
                flightDetails.getAvailableFares().forEach(fare ->
                        log.debug("[SAGA_DEBUG][{}]   - {} (seats: {}, price: {})",
                                sagaId, fare.getName(), calculateAvailableSeats(fare), fare.getPrice())
                );
            }

            // Build an informative event with detailed information about the fare check
            String failureReason = null;
            if (!fareIsActuallyAvailable) {
                if (requestedFare == null) {
                    failureReason = "Selected fare '" + request.getSelectedFareName() + "' not found.";
                } else if (calculateAvailableSeats(requestedFare) < requestedPassengerCount) {
                    failureReason = "Not enough seats available for the selected fare. " +
                            "Requested: " + requestedPassengerCount + ", Available: " +
                            calculateAvailableSeats(requestedFare);
                } else {
                    failureReason = "Not enough seats available for the selected fare or fare not found.";
                }
            }

            FareAvailabilityCheckedEvent fareEvent = FareAvailabilityCheckedEvent.builder()
                    .sagaId(sagaId)
                    .fareAvailable(fareIsActuallyAvailable)
                    .requestedFareName(request.getSelectedFareName())
                    .requestedCount(requestedPassengerCount)
                    .actualAvailableCount(requestedFare != null ? calculateAvailableSeats(requestedFare) : 0)
                    .pricePerPassengerForFare(requestedFare != null ? requestedFare.getPrice() : null)
                    .failureReason(failureReason)
                    .build();

            updateSagaState(sagaId, SagaStep.AWAITING_FARE_AVAILABILITY_RESPONSE, payload);
            rabbitTemplate.convertAndSend(eventsExchange, RK_INTERNAL_FARE_CHECKED_EVENT, fareEvent);
            log.info("[SAGA_STEP][{}] Published internal FareAvailabilityCheckedEvent.", sagaId);
        } catch (feign.FeignException.InternalServerError e) {
            log.error("[SAGA_ERROR][{}] Flight Service returned 500 error: {}", sagaId, e.contentUTF8(), e);
            handleSagaFailure(sagaId, SagaStep.FAILED_FETCH_FLIGHT_DETAILS,
                    "FLIGHT_SERVICE_500_ERROR: " + e.getMessage(), payload);
            bookingService.failSagaInitiationBeforePaymentUrl(sagaId, bookingReferenceForUser,
                    "Flight service temporarily unavailable. Please try again later.");
        } catch (feign.FeignException.NotFound e) {
            log.error("[SAGA_ERROR][{}] Flight ID not found: {}", sagaId, e.contentUTF8(), e);
            handleSagaFailure(sagaId, SagaStep.FAILED_FETCH_FLIGHT_DETAILS,
                    "FLIGHT_NOT_FOUND: " + e.getMessage(), payload);
            bookingService.failSagaInitiationBeforePaymentUrl(sagaId, bookingReferenceForUser,
                    "The selected flight is no longer available.");
        } catch (feign.RetryableException e) {
            log.error("[SAGA_ERROR][{}] Timeout or connection error with Flight Service: {}", sagaId, e.getMessage(), e);
            handleSagaFailure(sagaId, SagaStep.FAILED_FETCH_FLIGHT_DETAILS,
                    "FLIGHT_SERVICE_TIMEOUT: " + e.getMessage(), payload);
            bookingService.failSagaInitiationBeforePaymentUrl(sagaId, bookingReferenceForUser,
                    "Connection to flight service timed out. Please try again later.");
        } catch (SagaProcessingException e) {
            log.error("[SAGA_ERROR][{}] Business logic error: {}", sagaId, e.getMessage(), e);
            handleSagaFailure(sagaId, SagaStep.FAILED_PROCESSING,
                    "BUSINESS_ERROR: " + e.getMessage(), payload);
            bookingService.failSagaInitiationBeforePaymentUrl(sagaId, bookingReferenceForUser,
                    e.getMessage());
        } catch (Exception e) {
            log.error("[SAGA_ERROR][{}] Failed to get flight details for fare availability check: {}", sagaId, e.getMessage(), e);
            handleSagaFailure(sagaId, SagaStep.FAILED_FETCH_FLIGHT_DETAILS,
                    "FIS_CALL_FLIGHT_DETAILS_FAILED: " + e.getMessage(), payload);
            bookingService.failSagaInitiationBeforePaymentUrl(sagaId, bookingReferenceForUser,
                    "Failed to get flight details from Flight Service.");
        }
    }

    private void processMultiSegmentBooking(CreateBookingRequestDTO request, UUID userId, UUID sagaId,
                                            String bookingReferenceForUser, String clientIpAddress,
                                            CreateBookingSagaPayload payload) {
        int requestedPassengerCount = request.getPassengers().size();

        try {
            log.info("[SAGA_STEP][{}] Processing multi-segment booking for {} flight segments, fare: {}, passengers: {}",
                    sagaId, request.getFlightIds().size(), request.getSelectedFareName(), requestedPassengerCount);

            // Validate all flights and collect flight details
            List<Map<String, Object>> allFlightDetails = new ArrayList<>();
            boolean allFaresAvailable = true;
            String failureReason = null;

            for (int i = 0; i < request.getFlightIds().size(); i++) {
                UUID flightId = request.getFlightIds().get(i);

                log.info("[SAGA_STEP][{}] Checking flight segment {}/{}: {}",
                        sagaId, i + 1, request.getFlightIds().size(), flightId);

                try {
                    FsFlightWithFareDetailsDTO flightDetails = flightClient.getFlightDetails(flightId);

                    if (flightDetails == null || flightDetails.getAvailableFares() == null || flightDetails.getAvailableFares().isEmpty()) {
                        allFaresAvailable = false;
                        failureReason = "Flight segment " + (i + 1) + " has no available fares";
                        log.error("[SAGA_ERROR][{}] Flight segment {} has no available fares", sagaId, i + 1);
                        break;
                    }

                    // Log available fares for debugging
                    log.info("[SAGA_DEBUG][{}] Available fares for flight segment {}: {}", sagaId, i + 1,
                            flightDetails.getAvailableFares().stream()
                                    .map(fare -> fare.getName() + " (seats: " + calculateAvailableSeats(fare) + ")")
                                    .toList());

                    log.info("[SAGA_DEBUG][{}] Requested fare: '{}' for {} passengers", sagaId, request.getSelectedFareName(), requestedPassengerCount);

                    // Check if the requested fare exists and has enough seats for this segment
                    // Handle various fare name formats (case-insensitive and common variations)
                    boolean fareIsAvailableForThisSegment = flightDetails.getAvailableFares().stream()
                            .anyMatch(fare -> fare.getName() != null &&
                                    isFareNameMatch(fare.getName(), request.getSelectedFareName()) &&
                                    calculateAvailableSeats(fare) >= requestedPassengerCount);

                    if (!fareIsAvailableForThisSegment) {
                        allFaresAvailable = false;
                        failureReason = "Fare '" + request.getSelectedFareName() + "' not available for flight segment " + (i + 1);
                        log.error("[SAGA_ERROR][{}] Fare not available for flight segment {}", sagaId, i + 1);
                        break;
                    }

                    // Store flight details for later use
                    Map<String, Object> flightDetail = new HashMap<>();
                    flightDetail.put("flightId", flightId);
                    flightDetail.put("flightCode", flightDetails.getFlightCode());
                    flightDetail.put("originAirportCode", flightDetails.getOriginAirport().getCode());
                    flightDetail.put("destinationAirportCode", flightDetails.getDestinationAirport().getCode());
                    flightDetail.put("departureTime", flightDetails.getDepartureTime());
                    flightDetail.put("arrivalTime", flightDetails.getEstimatedArrivalTime());

                    allFlightDetails.add(flightDetail);

                } catch (feign.FeignException.NotFound e) {
                    allFaresAvailable = false;
                    failureReason = "Flight segment " + (i + 1) + " not found: " + flightId;
                    log.error("[SAGA_ERROR][{}] Flight segment {} not found: {}", sagaId, i + 1, flightId);
                    break;
                } catch (Exception e) {
                    allFaresAvailable = false;
                    failureReason = "Error checking flight segment " + (i + 1) + ": " + e.getMessage();
                    log.error("[SAGA_ERROR][{}] Error checking flight segment {}: {}", sagaId, i + 1, e.getMessage(), e);
                    break;
                }
            }

            if (allFaresAvailable) {
                log.info("[SAGA_STEP][{}] All flight segments validated successfully", sagaId);

                // All segments validated, proceed to create the multi-segment booking
                try {
                    // Get payment method from request - don't default if missing
                    String paymentMethod = request.getPaymentMethod();
                    if (paymentMethod == null || paymentMethod.isEmpty()) {
                        log.error("[SAGA_ERROR][{}] No payment method specified for multi-segment booking", sagaId);
                        throw new SagaProcessingException("Payment method is required");
                    }

                    // Validate that the payment method is a valid enum value
                    try {
                        com.boeing.bookingservice.model.enums.PaymentMethod paymentMethodEnum =
                                com.boeing.bookingservice.model.enums.PaymentMethod.valueOf(paymentMethod);
                        log.info("[SAGA_STEP][{}] Using payment method: {} for multi-segment booking", sagaId, paymentMethodEnum);
                    } catch (IllegalArgumentException e) {
                        log.error("[SAGA_ERROR][{}] Invalid payment method: {}", sagaId, paymentMethod);
                        throw new SagaProcessingException("Invalid payment method: " + paymentMethod);
                    }

                    CreatePendingMultiSegmentBookingInternalCommand command = CreatePendingMultiSegmentBookingInternalCommand.builder()
                            .sagaId(sagaId)
                            .bookingReference(bookingReferenceForUser)
                            .flightIds(request.getFlightIds())
                            .passengersInfo(request.getPassengers())
                            .selectedFareName(request.getSelectedFareName())
                            .selectedSeatsByFlight(request.getSelectedSeatsByFlight())
                            .userId(userId)
                            .totalAmount(request.getTotalAmount() != null ? request.getTotalAmount() : 0.0) // Use frontend amount
                            .discountAmount(0.0) // TODO: Handle voucher discounts
                            .appliedVoucherCode(request.getVoucherCode())
                            .paymentDeadline(LocalDateTime.now().plusMinutes(15))
                            .flightDetails(allFlightDetails)
                            .paymentMethod(paymentMethod)
                            .clientIpAddress(clientIpAddress)
                            .seatPricingByFlight(request.getSeatPricingByFlight()) // Pass frontend pricing
                            .baggageAddons(request.getBaggageAddons())
                            .build();

                    saveSagaState(sagaId, SagaStep.AWAITING_MULTI_SEGMENT_PENDING_BOOKING_CREATION, payload,
                            "All flight segments validated, creating multi-segment booking");

                    rabbitTemplate.convertAndSend(commandsExchange, RK_INTERNAL_CREATE_MULTI_SEGMENT_PENDING_BOOKING_CMD, command);

                    log.info("[SAGA_STEP][{}] Multi-segment booking command sent to queue", sagaId);

                } catch (Exception e) {
                    log.error("[SAGA_ERROR][{}] Failed to send multi-segment booking command: {}", sagaId, e.getMessage(), e);
                    handleSagaFailure(sagaId, SagaStep.FAILED_PROCESSING,
                            "Failed to send multi-segment booking command: " + e.getMessage(), payload);
                    bookingService.failSagaInitiationBeforePaymentUrl(sagaId, bookingReferenceForUser,
                            "Failed to process multi-segment booking request.");
                }

            } else {
                log.error("[SAGA_ERROR][{}] Multi-segment fare availability check failed: {}", sagaId, failureReason);
                handleSagaFailure(sagaId, SagaStep.FAILED_FARE_UNAVAILABLE, failureReason, payload);
                bookingService.failSagaInitiationBeforePaymentUrl(sagaId, bookingReferenceForUser, failureReason);
            }

        } catch (Exception e) {
            log.error("[SAGA_ERROR][{}] Unexpected error in multi-segment booking processing: {}", sagaId, e.getMessage(), e);
            handleSagaFailure(sagaId, SagaStep.FAILED_PROCESSING,
                    "Unexpected error in multi-segment processing: " + e.getMessage(), payload);
            bookingService.failSagaInitiationBeforePaymentUrl(sagaId, bookingReferenceForUser,
                    "An unexpected error occurred while processing your booking.");
        }
    }

    // Comprehensive fallback method for circuit breaker
    public void startSagaFallback(CreateBookingRequestDTO request, UUID userId, UUID sagaId, String bookingReferenceForUser,
                                  String clientIpAddress, Exception e) {
        log.warn("[SAGA_CIRCUIT_BREAKER][{}] Circuit breaker triggered for startSaga: {}",
                sagaId, e.getMessage());

        try {
            // Create a payload for the failure case
            CreateBookingSagaPayload payload = CreateBookingSagaPayload.builder()
                    .initialRequest(request)
                    .userId(userId)
                    .userFriendlyBookingReference(bookingReferenceForUser)
                    .clientIpAddress(clientIpAddress)
                    .build();

            // Get user-friendly version of the exception for logging and tracking
            String errorType = e.getClass().getSimpleName();
            String errorMessage = e.getMessage();

            log.info("[SAGA_FALLBACK][{}] Handling {} with message: {}", sagaId, errorType, errorMessage);

            // Record the circuit breaker activation in saga state with detailed info
            saveSagaState(sagaId, SagaStep.FAILED_CIRCUIT_BREAKER, payload,
                    String.format("Circuit breaker activated for %s: %s", errorType, errorMessage));

            // Provide a user-friendly error message based on the exception type
            String userMessage;
            if (e instanceof TimeoutException || e instanceof feign.RetryableException) {
                userMessage = "Flight service is temporarily unavailable due to high demand. Please try again in a few minutes.";
            } else if (e instanceof feign.FeignException.NotFound) {
                userMessage = "The flight you selected is no longer available.";
            } else if (e instanceof SagaProcessingException && e.getMessage().contains("fare")) {
                userMessage = "The fare you selected is no longer available.";
            } else {
                userMessage = "Flight service is currently unavailable. Please try again later.";
            }

            // Inform the client with appropriate message
            bookingService.failSagaInitiationBeforePaymentUrl(sagaId, bookingReferenceForUser, userMessage);

        } catch (Exception fallbackError) {
            log.error("[SAGA_ERROR][{}] Fallback method failed: {}", sagaId, fallbackError.getMessage(), fallbackError);
            try {
                // Last resort fallback
                bookingService.failSagaInitiationBeforePaymentUrl(sagaId, bookingReferenceForUser,
                        "System error during booking creation. Please try again later.");
            } catch (Exception e2) {
                log.error("[SAGA_ERROR][{}] Critical failure in circuit breaker fallback", sagaId, e2);
            }
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
            log.info("[SAGA_INFO][{}] Calculated totalAmountBeforeDiscount: {}", sagaId, bookingAmountBeforeDiscount);
            saveSagaState(sagaId, SagaStep.FARE_AVAILABILITY_CHECKED, payload, null);

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

        // Get payment method from request - don't default if missing
        String paymentMethod = currentPayload.getInitialRequest().getPaymentMethod();
        if (paymentMethod == null || paymentMethod.isEmpty()) {
            log.error("[SAGA_ERROR][{}] No payment method specified for booking", sagaId);
            handleSagaFailure(sagaId, SagaStep.FAILED_PROCESSING, "Payment method is required", currentPayload);
            bookingService.failSagaInitiationBeforePaymentUrl(sagaId, currentPayload.getUserFriendlyBookingReference(),
                    "Payment method is required");
            return;
        }

        // Validate that the payment method is a valid enum value
        try {
            com.boeing.bookingservice.model.enums.PaymentMethod paymentMethodEnum =
                    com.boeing.bookingservice.model.enums.PaymentMethod.valueOf(paymentMethod);
            log.info("[SAGA_STEP][{}] Using payment method: {} for single-segment booking", sagaId, paymentMethodEnum);
        } catch (IllegalArgumentException e) {
            log.error("[SAGA_ERROR][{}] Invalid payment method: {}", sagaId, paymentMethod);
            handleSagaFailure(sagaId, SagaStep.FAILED_PROCESSING, "Invalid payment method: " + paymentMethod, currentPayload);
            bookingService.failSagaInitiationBeforePaymentUrl(sagaId, currentPayload.getUserFriendlyBookingReference(),
                    "Invalid payment method: " + paymentMethod);
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
                .paymentMethod(paymentMethod)
                .snapshotFlightCode(flightDetailsSnapshot.getFlightCode())
                .snapshotOriginAirportCode(flightDetailsSnapshot.getOriginAirport().getCode())
                .snapshotDestinationAirportCode(flightDetailsSnapshot.getDestinationAirport().getCode())
                .snapshotDepartureTime(flightDetailsSnapshot.getDepartureTime())
                .snapshotArrivalTime(flightDetailsSnapshot.getEstimatedArrivalTime())
                .clientIpAddress(currentPayload.getClientIpAddress())
                .baggageAddons(currentPayload.getInitialRequest().getBaggageAddons())
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

        // FIXED: Skip AWAITING_PAYMENT_IPN, go directly to PENDING_BOOKING_CREATED
        // Payment processing will handle the transition to PAYMENT_COMPLETED
        saveSagaState(sagaId, SagaStep.PENDING_BOOKING_CREATED, payload, null);

        bookingService.completeSagaInitiationWithPaymentUrl(
                sagaId,
                payload.getUserFriendlyBookingReference(),
                payload.getFinalTotalAmount(),
                payload.getVnpayPaymentUrl(),
                payload.getPaymentDeadline());
        log.info("[SAGA_INFO][{}] Saga ready for payment. Client has been notified. UserBookingRef: {}",
                sagaId, payload.getUserFriendlyBookingReference());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancelSaga(UUID sagaId, String reason) {
        try {
            log.info("[SAGA_CANCEL][{}] Cancelling saga with reason: {}", sagaId, reason);

            SagaState sagaState = sagaStateRepository.findById(sagaId).orElse(null);
            if (sagaState != null) {
                sagaState.setCurrentStep(SagaStep.CANCELLED_CLIENT_TIMEOUT);
                sagaState.setErrorMessage("Saga cancelled: " + reason);
                sagaState.setUpdatedAt(LocalDateTime.now());
                sagaStateRepository.saveAndFlush(sagaState);
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private void saveSagaState(UUID sagaId, SagaStep currentStep, CreateBookingSagaPayload payload,
                               String errorMessage) {
        try {
            String serializedPayload = serializePayload(payload);

            // Use a pessimistic lock to avoid concurrent modifications
            SagaState sagaState = sagaStateRepository.findById(sagaId).orElse(null);
            boolean isNewSaga = (sagaState == null);

            if (isNewSaga) {
                // Create new saga state with error handling for duplicates
                try {
                    sagaState = SagaState.builder()
                            .sagaId(sagaId)
                            .createdAt(LocalDateTime.now())
                            .build();
                    log.info("[SAGA_STATE_CREATE] Creating new saga state for saga {} with step: {}", sagaId, currentStep);

                    sagaState.setCurrentStep(currentStep);
                    sagaState.setPayloadJson(serializedPayload);
                    sagaState.setErrorMessage(errorMessage);
                    sagaState.setUpdatedAt(LocalDateTime.now());
                    sagaStateRepository.saveAndFlush(sagaState); // Use saveAndFlush for atomic operation

                    log.info("[SAGA_STATE_CREATE] Created new saga state for saga {} with step: {}", sagaId, currentStep);
                } catch (org.springframework.dao.DataIntegrityViolationException e) {
                    // Handle case where saga state was created by another thread/process
                    log.warn("[SAGA_STATE_CREATE] Saga state already exists for sagaId: {} (race condition), updating instead", sagaId);
                    sagaState = sagaStateRepository.findById(sagaId).orElseThrow(() ->
                            new IllegalStateException("Saga state not found after duplicate key error for sagaId: " + sagaId));
                    updateExistingSagaState(sagaState, currentStep, serializedPayload, errorMessage);
                }
            } else {
                SagaStep oldStep = sagaState.getCurrentStep();
                log.info("[SAGA_STATE_UPDATE] Updating saga {} from step {} to step {}", sagaId, oldStep, currentStep);
                updateExistingSagaState(sagaState, currentStep, serializedPayload, errorMessage);
            }
        } catch (Exception e) {
            log.error("[SAGA_STATE_SAVE] Error saving saga state for saga: {}", sagaId, e);
            throw e;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private void updateExistingSagaState(SagaState sagaState, SagaStep currentStep,
                                         String serializedPayload, String errorMessage) {
        sagaState.setCurrentStep(currentStep);
        sagaState.setPayloadJson(serializedPayload);
        sagaState.setErrorMessage(errorMessage);
        sagaState.setUpdatedAt(LocalDateTime.now());
        sagaStateRepository.saveAndFlush(sagaState); // Use saveAndFlush for atomic operation
        log.debug("[SAGA_STATE_UPDATE] Successfully updated saga state for saga {}", sagaState.getSagaId());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private void updateExistingSagaState(SagaState sagaState, SagaStep currentStep,
                                         CreateBookingSagaPayload payload, String errorMessage) {
        String serializedPayload = serializePayload(payload);
        updateExistingSagaState(sagaState, currentStep, serializedPayload, errorMessage);
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
                .filter(fare -> fare.getName() != null && isFareNameMatch(fare.getName(), selectedFareName))
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

    @CircuitBreaker(name = "flightServiceCircuitBreaker")
    @Retry(name = "flightServiceRetry")
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

            // Add timeout to prevent indefinite waiting
            long startTime = System.currentTimeMillis();
            long timeout = 30000; // 30 seconds timeout (increased from 5 seconds)

            // Introduce more robust error handling around the API call
            FsSeatsAvailabilityResponseDTO response = null;
            try {
                response = flightClient.checkSeatsAvailability(flightId, requestedSeatCodes);

                // Check for timeout
                long endTime = System.currentTimeMillis();
                long duration = endTime - startTime;
                log.info("[SAGA_STEP][{}] Seat availability check completed in {} ms", sagaId, duration);

                if (duration > timeout) {
                    log.warn("[SAGA_STEP][{}] Seat availability check took longer than expected: {} ms", sagaId, duration);
                }
            } catch (feign.FeignException.InternalServerError e) {
                log.error("[SAGA_ERROR][{}] Flight Service returned 500 error for seat check: {}",
                        sagaId, e.contentUTF8(), e);
                throw new SagaProcessingException("Flight service internal error: " + e.getMessage());
            } catch (feign.FeignException.BadRequest e) {
                log.error("[SAGA_ERROR][{}] Flight Service returned 400 error for seat check: {}",
                        sagaId, e.contentUTF8(), e);
                throw new SagaProcessingException("Invalid seat request: " + e.getMessage());
            } catch (feign.FeignException.NotFound e) {
                log.error("[SAGA_ERROR][{}] Flight or seat not found: {}", sagaId, e.contentUTF8(), e);
                throw new SagaProcessingException("Flight or seats not found: " + e.getMessage());
            } catch (feign.RetryableException e) {
                log.error("[SAGA_ERROR][{}] Timeout connecting to Flight Service: {}", sagaId, e.getMessage(), e);
                throw new SagaProcessingException("Flight service timeout: " + e.getMessage());
            }

            // Thorough response validation
            if (response == null) {
                throw new SagaProcessingException("Null response received from Flight Service seat availability check");
            }

            if (response.getSeatStatuses() == null) {
                log.warn("[SAGA_STEP][{}] Received malformed seat status response - missing seat statuses array", sagaId);
                throw new SagaProcessingException("Invalid response format from Flight Service - missing seat statuses");
            }

            // Verify seat count in response matches request
            if (response.getSeatStatuses().size() != requestedSeatCodes.size()) {
                log.warn("[SAGA_STEP][{}] Seat status count mismatch. Requested: {}, Received: {}",
                        sagaId, requestedSeatCodes.size(), response.getSeatStatuses().size());
                log.warn("[SAGA_STEP][{}] Requested: {}", sagaId, requestedSeatCodes);
                log.warn("[SAGA_STEP][{}] Received: {}", sagaId,
                        response.getSeatStatuses().stream().map(s -> s.getSeatCode()).toList());
                throw new SagaProcessingException("Seat status count mismatch from Flight Service");
            }

            // Check if all required seats are in the response
            List<String> receivedSeatCodes = response.getSeatStatuses().stream()
                    .map(status -> status.getSeatCode())
                    .toList();

            for (String requestedSeat : requestedSeatCodes) {
                if (!receivedSeatCodes.contains(requestedSeat)) {
                    log.error("[SAGA_ERROR][{}] Requested seat {} missing from response", sagaId, requestedSeat);
                    throw new SagaProcessingException("Incomplete seat availability response from Flight Service");
                }
            }

            // Extract unavailable seats for better user messaging
            List<String> unavailableSeats = response.getSeatStatuses().stream()
                    .filter(status -> !status.isAvailable())
                    .map(status -> status.getSeatCode())
                    .toList();

            boolean allSeatsAvailable = unavailableSeats.isEmpty();

            // Build comprehensive event with detailed information
            SeatAvailabilityCheckedEvent event = SeatAvailabilityCheckedEvent.builder()
                    .sagaId(sagaId)
                    .flightId(payload.getInitialRequest().getFlightId())
                    .requestedSeatCodes(requestedSeatCodes)
                    .allSeatsAvailable(allSeatsAvailable)
                    .unavailableSeats(allSeatsAvailable ? null : unavailableSeats)
                    .failureReason(allSeatsAvailable ? null :
                            "The following seats are not available: " + String.join(", ", unavailableSeats))
                    .build();

            rabbitTemplate.convertAndSend(eventsExchange, RK_INTERNAL_SEAT_AVAILABILITY_CHECKED_EVENT, event);
            log.info("[SAGA_STEP][{}] Published SeatAvailabilityCheckedEvent. All seats available: {}",
                    sagaId, allSeatsAvailable);

            if (!allSeatsAvailable) {
                log.warn("[SAGA_STEP][{}] Some requested seats are not available: {}", sagaId, unavailableSeats);
            }

        } catch (Exception e) {
            log.error("[SAGA_ERROR][{}] Failed to check seat availability: {}", sagaId, e.getMessage(), e);
            handleSeatAvailabilityError(sagaId, payload, "SEAT_AVAILABILITY_CHECK_FAILED: " + e.getMessage());
        }
    }

    // Centralized method to handle seat availability errors
    private void handleSeatAvailabilityError(UUID sagaId, CreateBookingSagaPayload payload, String errorReason) {
        try {
            // Extract error type for more specific handling
            String errorType = errorReason.split(":")[0].trim();

            // For certain error types, we might want to proceed anyway (this is a business decision)
            if (errorType.equals("FLIGHT_SERVICE_500_ERROR") ||
                    errorType.equals("FLIGHT_SERVICE_TIMEOUT")) {

                log.warn("[SAGA_RECOVERY][{}] Attempting recovery from Flight Service error: {}", sagaId, errorType);
                saveSagaState(sagaId, SagaStep.SEAT_AVAILABILITY_CHECKED, payload,
                        "Seat check bypassed due to Flight Service error: " + errorReason);
                sendCreatePendingBookingInternalCommand(sagaId, payload);
                return;
            }

            // Handle specific error cases with user-friendly messages
            String userMessage;
            switch (errorType) {
                case "INVALID_REQUEST":
                    userMessage = "Invalid seat selection. Please try again with different seats.";
                    break;
                case "RESOURCE_NOT_FOUND":
                    userMessage = "The selected flight or seats are no longer available.";
                    break;
                default:
                    userMessage = "Failed to check seat availability. Please try again later.";
            }

            // For other errors, fail the saga
            handleSagaFailure(sagaId, SagaStep.FAILED_PROCESSING, errorReason, payload);
            bookingService.failSagaInitiationBeforePaymentUrl(sagaId, payload.getUserFriendlyBookingReference(),
                    userMessage);

        } catch (Exception fallbackError) {
            log.error("[SAGA_ERROR][{}] Error handling failed: {}", sagaId, fallbackError.getMessage(), fallbackError);
            handleSagaFailure(sagaId, SagaStep.FAILED_PROCESSING,
                    "SEAT_AVAILABILITY_ERROR_HANDLING_FAILED: " + fallbackError.getMessage(), payload);
            bookingService.failSagaInitiationBeforePaymentUrl(sagaId, payload.getUserFriendlyBookingReference(),
                    "System error during seat availability check. Please try again later.");
        }
    }

    /**
     * Checks if a fare name from the flight service matches the requested fare name.
     * Handles case-insensitive matching and common fare name variations.
     */
    private boolean isFareNameMatch(String availableFareName, String requestedFareName) {
        if (availableFareName == null || requestedFareName == null) {
            return false;
        }

        // Normalize both names for comparison
        String normalizedAvailable = normalizeFareName(availableFareName);
        String normalizedRequested = normalizeFareName(requestedFareName);

        return normalizedAvailable.equals(normalizedRequested);
    }

    /**
     * Normalizes fare names to handle different naming conventions.
     */
    private String normalizeFareName(String fareName) {
        if (fareName == null) {
            return "";
        }

        String normalized = fareName.toLowerCase()
                .replace("_", " ")
                .replace("-", " ")
                .trim();

        // Handle common variations
        if (normalized.contains("first")) {
            return "first";
        } else if (normalized.contains("business")) {
            return "business";
        } else if (normalized.contains("economy") || normalized.contains("coach")) {
            return "economy";
        } else if (normalized.contains("premium")) {
            return "premium";
        }

        return normalized;
    }

    /**
     * Helper method to calculate available seats for a fare
     *
     * @param fare The fare to calculate available seats for
     * @return Number of available seats
     */
    private int calculateAvailableSeats(FsDetailedFareDTO fare) {
        if (fare == null) {
            return 0;
        }

        int totalSeats = fare.getTotalSeats();
        int occupiedSeats = fare.getOccupiedSeats() != null ? fare.getOccupiedSeats().size() : 0;

        return Math.max(0, totalSeats - occupiedSeats);
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

    @RabbitListener(queues = "${app.rabbitmq.queues.internalSeatAvailabilityChecked}")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleSeatAvailabilityChecked(SeatAvailabilityCheckedEvent event) {
        UUID sagaId = event.getSagaId();
        log.info("[SAGA_EVENT][{}] Received SeatAvailabilityCheckedEvent. All seats available: {}",
                sagaId, event.isAllSeatsAvailable());

        SagaState sagaState = loadSagaState(sagaId);
        CreateBookingSagaPayload payload = deserializePayload(sagaState.getPayloadJson());

        if (!event.isAllSeatsAvailable()) {
            String failureReason = event.getFailureReason();
            log.warn("[SAGA_EVENT][{}] Seats unavailable: {}", sagaId, failureReason);
            handleSagaFailure(sagaId, SagaStep.FAILED_SEAT_UNAVAILABLE, failureReason, payload);
            bookingService.failSagaInitiationBeforePaymentUrl(sagaId, payload.getUserFriendlyBookingReference(),
                    failureReason != null ? failureReason : "Selected seats are not available.");
            return;
        }

        // Mark the step as completed
        saveSagaState(sagaId, SagaStep.SEAT_AVAILABILITY_CHECKED, payload, null);
        log.info("[SAGA_STEP][{}] Seat availability check successful, proceeding to create pending booking.", sagaId);

        // Proceed with creating the pending booking
        sendCreatePendingBookingInternalCommand(sagaId, payload);
    }
}