package com.boeing.bookingservice.service.impl;

import com.boeing.bookingservice.dto.request.*;
import com.boeing.bookingservice.dto.response.ApiResponse;
import com.boeing.bookingservice.dto.response.BookingFullDetailResponseDTO;
import com.boeing.bookingservice.dto.response.BookingInitiatedResponseDTO;
import com.boeing.bookingservice.dto.response.BookingSummaryDTO;
import com.boeing.bookingservice.event.BookingCompletedEvent;
import com.boeing.bookingservice.exception.BadRequestException;
import com.boeing.bookingservice.exception.ResourceNotFoundException;
import com.boeing.bookingservice.exception.SagaProcessingException;
import com.boeing.bookingservice.integration.fs.FlightClient;
import com.boeing.bookingservice.integration.fs.dto.FsReleaseFareRequestDTO;
import com.boeing.bookingservice.integration.ls.LoyaltyClientWrapper;
import com.boeing.bookingservice.integration.ls.dto.LsUserVoucherDTO;
import com.boeing.bookingservice.mapper.BookingMapper;
import com.boeing.bookingservice.mapper.PassengerMapper;
import com.boeing.bookingservice.model.entity.*;
import com.boeing.bookingservice.model.enums.*;
import com.boeing.bookingservice.repository.BookingRepository;
import com.boeing.bookingservice.repository.PassengerRepository;
import com.boeing.bookingservice.repository.PaymentRepository;
import com.boeing.bookingservice.repository.specifications.BookingSpecifications;
import com.boeing.bookingservice.saga.orchestrator.CreateBookingSagaOrchestrator;
import com.boeing.bookingservice.service.BookingService;
import com.boeing.bookingservice.service.PaymentService;
import com.boeing.bookingservice.util.BookingUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingServiceImpl implements BookingService {

    private final CreateBookingSagaOrchestrator createBookingSagaOrchestrator;
    private final BookingRepository bookingRepository;
    private final PassengerRepository passengerRepository;
    private final PaymentRepository paymentRepository;
    private final BookingMapper bookingMapper;
    private final PassengerMapper passengerMapper;
    private final FlightClient flightClient;
    private final LoyaltyClientWrapper loyaltyClient;
    @Lazy
    private final PaymentService paymentService;
    private final ApplicationEventPublisher eventPublisher;
    private final BaggageAddonServiceImpl baggageAddonService;

    private final Map<UUID, CompletableFuture<BookingInitiatedResponseDTO>> pendingSagaResponses = new ConcurrentHashMap<>();

    /**
     * Initiates a booking creation saga. This method validates the request and
     * starts
     * the saga process for both single-segment and multi-segment bookings.
     * Transaction is not started here since this method only validates and
     * delegates to the saga.
     */
    @Override
    public BookingInitiatedResponseDTO initiateBookingCreationSaga(CreateBookingRequestDTO createBookingRequest,
            UUID userId, String clientIpAddress) {
        // Handle new multi-segment format or legacy single-segment format
        // A booking is multi-segment if it has more than 1 flight ID
        boolean isMultiSegment = createBookingRequest.getFlightIds() != null
                && createBookingRequest.getFlightIds().size() > 1;

        if (isMultiSegment) {
            log.info("Initiating multi-segment booking creation saga for user: {} with {} flight segments from IP: {}",
                    userId, createBookingRequest.getFlightIds().size(), clientIpAddress);

            // Log seat selections for multi-segment booking - detailed logging
            if (createBookingRequest.getSelectedSeatsByFlight() != null
                    && !createBookingRequest.getSelectedSeatsByFlight().isEmpty()) {
                log.info("[SEAT_DEBUG] Multi-segment request has seat selections for {} flights:",
                        createBookingRequest.getSelectedSeatsByFlight().size());
                createBookingRequest.getSelectedSeatsByFlight().forEach((flightIndex, seats) -> {
                    log.info("[SEAT_DEBUG] Flight[{}]: {} seats: {}", flightIndex, seats.size(), seats);
                });
            } else {
                log.info("[SEAT_DEBUG] Multi-segment request doesn't have selectedSeatsByFlight!");

                // Check if we have seat selections in the alternative format
                if (createBookingRequest.getSeatSelections() != null
                        && !createBookingRequest.getSeatSelections().isEmpty()) {
                    log.info("[SEAT_DEBUG] Multi-segment has {} seat selections in seatSelections array",
                            createBookingRequest.getSeatSelections().size());

                    // If we have seat selections in the array but not in the map, let's convert
                    // them
                    try {
                        Map<String, List<String>> seatsByFlight = new HashMap<>();

                        for (int i = 0; i < createBookingRequest.getSeatSelections().size(); i++) {
                            var seatSelection = createBookingRequest.getSeatSelections().get(i);
                            String flightId = createBookingRequest.getFlightIds().get(0).toString();

                            if (!seatsByFlight.containsKey(flightId)) {
                                seatsByFlight.put(flightId, new ArrayList<>());
                            }

                            seatsByFlight.get(flightId).add(seatSelection.getSeatCode());
                            log.info("[SEAT_DEBUG] Adding seat {} for passenger {} to flight {}",
                                    seatSelection.getSeatCode(),
                                    seatSelection.getPassengerIndex(),
                                    flightId);
                        }

                        if (!seatsByFlight.isEmpty()) {
                            log.info("[SEAT_DEBUG] Created seat map with {} flight entries", seatsByFlight.size());
                            createBookingRequest.setSelectedSeatsByFlight(seatsByFlight);

                            // Log the converted structure
                            createBookingRequest.getSelectedSeatsByFlight().forEach((flightId, seats) -> {
                                log.info("[SEAT_DEBUG] Converted Flight[{}]: {} seats: {}", flightId, seats.size(),
                                        seats);
                            });
                        }
                    } catch (Exception e) {
                        log.error("[SEAT_DEBUG] Failed to convert seat selections: {}", e.getMessage(), e);
                    }
                } else {
                    log.warn("[SEAT_DEBUG] Multi-segment request DOESN'T have ANY seat selections!");
                }
            }
        } else {
            log.info("Initiating single-segment booking creation saga for user: {} with flightId: {} from IP: {}",
                    userId, createBookingRequest.getFlightId(), clientIpAddress);

            // Log seat selections for single-segment booking - enhanced with error recovery
            if (createBookingRequest.getSeatSelections() != null
                    && !createBookingRequest.getSeatSelections().isEmpty()) {
                log.info("[SEAT_DEBUG] Single-segment request has {} seat selections:",
                        createBookingRequest.getSeatSelections().size());
                for (int i = 0; i < createBookingRequest.getSeatSelections().size(); i++) {
                    var seat = createBookingRequest.getSeatSelections().get(i);
                    log.info("[SEAT_DEBUG] Seat[{}]: code={}, passengerIndex={}",
                            i, seat.getSeatCode(), seat.getPassengerIndex());
                }
            } else {
                log.info("[SEAT_DEBUG] Single-segment request doesn't have seatSelections array!");

                // Check if we have seat selections in the alternative format
                if (createBookingRequest.getSelectedSeatsByFlight() != null
                        && !createBookingRequest.getSelectedSeatsByFlight().isEmpty()) {
                    log.info("[SEAT_DEBUG] Single-segment has selectedSeatsByFlight with {} entries",
                            createBookingRequest.getSelectedSeatsByFlight().size());

                    // Convert map format to list format
                    try {
                        List<SeatSelectionDTO> seatSelections = new ArrayList<>();
                        String flightIdStr = createBookingRequest.getFlightId().toString();

                        // Try to find matching flight ID in the map
                        List<String> seatCodes = null;
                        if (createBookingRequest.getSelectedSeatsByFlight().containsKey(flightIdStr)) {
                            seatCodes = createBookingRequest.getSelectedSeatsByFlight().get(flightIdStr);
                            log.info("[SEAT_DEBUG] Found seats for flight ID {}: {}", flightIdStr, seatCodes);
                        } else {
                            // Try to use the first entry as fallback
                            Map.Entry<String, List<String>> firstEntry = createBookingRequest.getSelectedSeatsByFlight()
                                    .entrySet().iterator().next();
                            seatCodes = firstEntry.getValue();
                            log.info("[SEAT_DEBUG] Using seats from first entry {}: {}", firstEntry.getKey(),
                                    seatCodes);
                        }

                        // Convert to SeatSelectionDTO objects
                        if (seatCodes != null) {
                            for (int i = 0; i < seatCodes.size(); i++) {
                                SeatSelectionDTO dto = new SeatSelectionDTO();
                                dto.setSeatCode(seatCodes.get(i));
                                dto.setPassengerIndex(i);
                                dto.setSelectedFareName(createBookingRequest.getSelectedFareName());
                                seatSelections.add(dto);

                                log.info("[SEAT_DEBUG] Created SeatSelectionDTO: seatCode={}, passengerIndex={}",
                                        dto.getSeatCode(), dto.getPassengerIndex());
                            }

                            createBookingRequest.setSeatSelections(seatSelections);
                            log.info("[SEAT_DEBUG] Created {} SeatSelectionDTO objects from selectedSeatsByFlight",
                                    seatSelections.size());
                        }
                    } catch (Exception e) {
                        log.error("[SEAT_DEBUG] Failed to convert selectedSeatsByFlight to seatSelections: {}",
                                e.getMessage(), e);
                    }
                } else {
                    log.warn("[SEAT_DEBUG] Single-segment request DOESN'T have ANY seat selections!");
                }
            }
        }

        // Validation based on booking type
        if (isMultiSegment) {
            // Multi-segment validation
            if (createBookingRequest.getFlightIds().isEmpty() || createBookingRequest.getPassengers() == null
                    || createBookingRequest.getPassengers().isEmpty()) {
                throw new BadRequestException("Flight IDs list and passenger information are required.");
            }
        } else {
            // Single-segment validation
            if (createBookingRequest.getFlightId() == null || createBookingRequest.getPassengers() == null
                    || createBookingRequest.getPassengers().isEmpty()) {
                throw new BadRequestException("Flight ID and passenger information are required.");
            }

            // Convert single flight ID to list for unified processing
            createBookingRequest.setFlightIds(List.of(createBookingRequest.getFlightId()));
        }

        // Common validation
        // Validate passenger count
        if (createBookingRequest.getPassengers().size() > 10) {
            throw new BadRequestException("Maximum 10 passengers allowed per booking.");
        }

        if (!StringUtils.hasText(createBookingRequest.getPaymentMethod())) {
            throw new BadRequestException("Payment method is required.");
        }
        if (!StringUtils.hasText(createBookingRequest.getSelectedFareName())) {
            throw new BadRequestException("Selected fare name is required.");
        }

        UUID bookingId = UUID.randomUUID();

        UUID sagaId = bookingId; // Use booking ID as saga ID for consistency
        String bookingReferenceForUser = "BKG-" + sagaId.toString().substring(0, 8).toUpperCase();

        CompletableFuture<BookingInitiatedResponseDTO> sagaResultFuture = new CompletableFuture<>();
        pendingSagaResponses.put(sagaId, sagaResultFuture);

        createBookingSagaOrchestrator.startSaga(createBookingRequest, userId, sagaId, bookingReferenceForUser,
                clientIpAddress);
        log.info("[BOOKING_INIT][SagaID:{}] Saga started with UserBookingRef: {}", sagaId, bookingReferenceForUser);

        try {
            return sagaResultFuture.get(60, TimeUnit.SECONDS); // 60 seconds timeout
        } catch (TimeoutException e) {
            log.error("[BOOKING_INIT_TIMEOUT][SagaID:{}] Timeout waiting for saga result. UserBookingRef: {}", sagaId,
                    bookingReferenceForUser, e);
            pendingSagaResponses.remove(sagaId);

            // Try a graceful cancel with diagnostic info for recovery
            try {
                // Record the timeout event for monitoring
                recordSagaTimeout(sagaId, bookingReferenceForUser);

                // Cancel saga but let it continue in background for potential completion
                createBookingSagaOrchestrator.cancelSaga(sagaId, "CLIENT_API_TIMEOUT");

                // Return a specific error code that the frontend can handle
                throw new SagaProcessingException("BOOKING_TIMEOUT: Booking process is taking longer than expected. " +
                        "You can check your bookings in a few minutes to see if it was completed. Ref: "
                        + bookingReferenceForUser, e);
            } catch (Exception cancelError) {
                log.error("[BOOKING_CANCEL_ERROR][SagaID:{}] Error during saga cancellation: {}", sagaId,
                        cancelError.getMessage());
                throw new SagaProcessingException(
                        "Booking processing timed out and cancellation failed. Please check your bookings later. Ref: "
                                + bookingReferenceForUser,
                        e);
            }
        } catch (InterruptedException e) {
            log.error("[BOOKING_INIT_INTERRUPTED][SagaID:{}] Saga result future interrupted. UserBookingRef: {}",
                    sagaId, bookingReferenceForUser, e);
            pendingSagaResponses.remove(sagaId);
            Thread.currentThread().interrupt();
            throw new SagaProcessingException(
                    "Booking processing was interrupted. Please check your bookings later. Ref: "
                            + bookingReferenceForUser,
                    e);
        } catch (ExecutionException e) {
            log.error("[BOOKING_INIT_EXECUTION_ERROR][SagaID:{}] Error executing saga. UserBookingRef: {}", sagaId,
                    bookingReferenceForUser, e.getCause());
            pendingSagaResponses.remove(sagaId);

            // Provide more specific error information
            if (e.getCause() instanceof SagaProcessingException) {
                SagaProcessingException sagaEx = (SagaProcessingException) e.getCause();
                // Add the booking reference for traceability
                throw new SagaProcessingException(sagaEx.getMessage() + " Ref: " + bookingReferenceForUser, sagaEx);
            }

            throw new SagaProcessingException(
                    "Failed to process booking. Please try again. Ref: " + bookingReferenceForUser, e);
        }
    }

    /**
     * Creates a pending booking in the database for a single flight segment.
     * Uses REQUIRES_NEW to ensure a new transaction is always started,
     * with REPEATABLE_READ isolation to prevent dirty reads during booking
     * creation.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.REPEATABLE_READ)
    @Override
    public Booking createPendingBookingInDatabase(
            UUID bookingId,
            String bookingReferenceForDisplay,
            UUID flightId,
            List<PassengerInfoDTO> passengerInfosFromRequest,
            String selectedFareNameFromRequest,
            List<SeatSelectionDTO> seatSelections,
            UUID userId,
            Double totalAmount,
            Double discountAmount,
            String appliedVoucherCode,
            LocalDateTime paymentDeadline,
            String snapshotFlightCode,
            String snapshotOriginAirportCode,
            String snapshotDestinationAirportCode,
            LocalDateTime snapshotDepartureTime,
            LocalDateTime snapshotArrivalTime,
            String paymentMethodFromRequest,
            String clientIpAddress,
            List<BaggageAddonRequestDTO> baggageAddons) {
        log.info("Service: Creating pending booking in DB. ID: {}, Ref: {}", bookingId, bookingReferenceForDisplay);

        // DEBUG: Log seat selections parameter
        if (seatSelections != null && !seatSelections.isEmpty()) {
            log.info("[SEAT_DEBUG] createPendingBookingInDatabase có {} seat selections:", seatSelections.size());
            for (int i = 0; i < seatSelections.size(); i++) {
                var seat = seatSelections.get(i);
                log.info("[SEAT_DEBUG] SeatParam[{}]: code={}, passengerIndex={}",
                        i, seat.getSeatCode(), seat.getPassengerIndex());
            }
        } else {
            log.info("[SEAT_DEBUG] createPendingBookingInDatabase KHÔNG có seat selections!");
        }

        Booking booking = Booking.builder()
                .id(bookingId)
                .bookingReference(bookingReferenceForDisplay)
                .userId(userId)
                .bookingDate(LocalDate.now())
                .totalAmount(totalAmount)
                .status(BookingStatus.PENDING_PAYMENT)
                .type(BookingType.STANDARD)
                .paymentDeadline(paymentDeadline)
                .appliedVoucherCode(appliedVoucherCode)
                .build();

        List<BookingDetail> bookingDetailsList = new ArrayList<>();
        int passengerCount = passengerInfosFromRequest.size();
        if (passengerCount == 0) {
            throw new BadRequestException("Passenger list cannot be empty for booking.");
        }

        // Calculate base fare price per passenger (fallback for pricing)
        double baseFarePrice = totalAmount / passengerCount;
        double actualTotalAmount = 0.0;

        // Validate seat selections and convert to a map for easier processing
        Map<Integer, String> seatSelectionMap = validateAndMapSeatSelections(seatSelections, passengerInfosFromRequest);

        // Use the common method to process passengers
        Map<Integer, Passenger> passengerEntities = processPassengers(passengerInfosFromRequest, userId);

        // Process fare information for mixed fare bookings
        final String defaultFareName = selectedFareNameFromRequest;

        // Create booking details for all passengers using the utility method
        // Ensure the booking code has the proper format with segment suffix (0-indexed
        // for first segment)
        String formattedBookingCode = BookingUtils.formatBookingCode(bookingReferenceForDisplay, 0);

        BookingUtils.BookingDetailsResult result = BookingUtils.createBookingDetailsForSegment(
                booking,
                passengerEntities,
                passengerInfosFromRequest,
                flightId,
                snapshotFlightCode,
                snapshotOriginAirportCode,
                snapshotDestinationAirportCode,
                snapshotDepartureTime,
                snapshotArrivalTime,
                defaultFareName,
                seatSelectionMap,
                baseFarePrice,
                formattedBookingCode);

        // Use the results
        bookingDetailsList.addAll(result.getDetails());
        actualTotalAmount = result.getTotalAmount();
        booking.setBookingDetails(bookingDetailsList);

        // Update booking with calculated actual total amount
        actualTotalAmount = Math.round(actualTotalAmount * 100.0) / 100.0;
        booking.setTotalAmount(actualTotalAmount);

        log.info("Service: Calculated actual total amount: {} (original: {}) for booking {}",
                actualTotalAmount, totalAmount, bookingReferenceForDisplay);

        Booking savedBooking = bookingRepository.save(booking);
        log.info(
                "Service: Pending booking entities (Booking, BookingDetail, Passenger) created successfully. DB ID: {}, Ref: {}",
                savedBooking.getId(), savedBooking.getBookingReference());

        // Process baggage add-ons if provided
        processBaggageAddonsForBooking(savedBooking, baggageAddons);

        // Apply 10% tax to the total amount
        double totalWithTax = savedBooking.getTotalAmount() * 1.10;
        totalWithTax = Math.round(totalWithTax * 100.0) / 100.0;
        savedBooking.setTotalAmount(totalWithTax);

        log.info("Service: Applied 10% tax. Final total amount: {} for booking {}",
                totalWithTax, bookingReferenceForDisplay);

        // Save booking again to persist updated total amount that includes baggage
        // costs and tax
        savedBooking = bookingRepository.save(savedBooking);

        try {
            CreatePaymentRequest paymentReq = new CreatePaymentRequest();
            paymentReq.setBookingReference(savedBooking.getBookingReference());
            paymentReq.setAmount(savedBooking.getTotalAmount());
            paymentReq.setOrderDescription("Thanh toan don hang " + savedBooking.getBookingReference());
            paymentReq.setPaymentMethod(paymentMethodFromRequest);
            paymentReq.setClientIpAddress(clientIpAddress);

            log.info("[PAYMENT_DEBUG] Creating VNPAY URL with paymentMethod: {}", paymentMethodFromRequest);
            String vnpayPaymentUrl = paymentService.createVNPayPaymentUrl(paymentReq, userId);

            if (!StringUtils.hasText(vnpayPaymentUrl)) {
                log.error("[VNPAY_URL_ERROR][ID:{}, Ref:{}] PaymentService returned empty VNPAY payment URL.",
                        savedBooking.getId(), savedBooking.getBookingReference());
                failSagaInitiationBeforePaymentUrl(savedBooking.getId(), savedBooking.getBookingReference(),
                        "Failed to generate VNPAY payment URL (empty URL returned).");
                throw new SagaProcessingException(
                        "Failed to generate VNPAY payment URL for " + savedBooking.getBookingReference());
            }

            completeSagaInitiationWithPaymentUrl(
                    savedBooking.getId(),
                    savedBooking.getBookingReference(),
                    savedBooking.getTotalAmount(),
                    vnpayPaymentUrl,
                    savedBooking.getPaymentDeadline());
        } catch (Exception e) {
            log.error("[VNPAY_URL_ERROR][ID:{}, Ref:{}] Exception while getting VNPAY payment URL: {}",
                    savedBooking.getId(), savedBooking.getBookingReference(), e.getMessage(), e);
            failSagaInitiationBeforePaymentUrl(savedBooking.getId(), savedBooking.getBookingReference(),
                    "Failed to generate VNPAY payment URL: " + e.getMessage());
            throw new SagaProcessingException(
                    "Failed to generate VNPAY payment URL for booking " + savedBooking.getBookingReference(), e);
        }
        return savedBooking;
    }

    /**
     * Creates a pending booking in the database for multiple flight segments
     * (connecting flights).
     * Uses REQUIRES_NEW to ensure a new transaction is always started,
     * with REPEATABLE_READ isolation to prevent dirty reads during booking
     * creation.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.REPEATABLE_READ)
    @Override
    public Booking createPendingMultiSegmentBookingInDatabase(
            UUID bookingId,
            String bookingReferenceForDisplay,
            List<UUID> flightIds,
            List<PassengerInfoDTO> passengerInfosFromRequest,
            String selectedFareNameFromRequest,
            Map<String, List<String>> selectedSeatsByFlight,
            UUID userId,
            Double totalAmount,
            Double discountAmount,
            String appliedVoucherCode,
            LocalDateTime paymentDeadline,
            List<Map<String, Object>> flightDetails,
            String paymentMethodFromRequest,
            String clientIpAddress,
            List<List<Map<String, Object>>> seatPricingByFlight,
            List<BaggageAddonRequestDTO> baggageAddons) {

        log.info("Service: Creating pending multi-segment booking in DB. ID: {}, Ref: {}, Segments: {}",
                bookingId, bookingReferenceForDisplay, flightIds.size());

        // Debug: Log seat selections
        if (selectedSeatsByFlight != null && !selectedSeatsByFlight.isEmpty()) {
            log.info("[MULTI_SEGMENT] Seat selections by flight: {}", selectedSeatsByFlight.size());
            selectedSeatsByFlight.forEach((flightIndex, seats) -> {
                log.info("[MULTI_SEGMENT] Flight index {}: {} seats", flightIndex, seats.size());
            });
        } else {
            log.info("[MULTI_SEGMENT] No seat selections provided");
        }

        // Create booking entity - determine correct booking type based on flight count
        BookingType bookingType = flightIds.size() > 1 ? BookingType.MULTI_SEGMENT : BookingType.STANDARD;

        log.info("Service: Setting booking type to {} based on {} flight segments",
                bookingType, flightIds.size());

        Booking booking = Booking.builder()
                .id(bookingId)
                .bookingReference(bookingReferenceForDisplay)
                .userId(userId)
                .bookingDate(LocalDate.now())
                .totalAmount(totalAmount)
                .status(BookingStatus.PENDING_PAYMENT)
                .type(bookingType)
                .paymentDeadline(paymentDeadline)
                .appliedVoucherCode(appliedVoucherCode)
                .build();

        List<BookingDetail> allBookingDetails = new ArrayList<>();
        int passengerCount = passengerInfosFromRequest.size();

        if (passengerCount == 0) {
            throw new BadRequestException("Passenger list cannot be empty for booking.");
        }

        // Use actual pricing from frontend instead of calculating equal pricing
        log.info("[MULTI_SEGMENT] Using frontend pricing information for {} flight segments", flightIds.size());
        double actualTotalAmount = 0.0;

        // Use common method to process passengers - reduces code duplication
        Map<Integer, Passenger> passengerEntities = processPassengers(passengerInfosFromRequest, userId);

        // Process each flight segment
        for (int flightIndex = 0; flightIndex < flightIds.size(); flightIndex++) {
            UUID flightId = flightIds.get(flightIndex);
            Map<String, Object> flightDetail = flightDetails.get(flightIndex);

            log.debug("[MULTI_SEGMENT] Processing flight {}: {}", flightIndex, flightDetail);

            String flightCode = (String) flightDetail.get("flightCode");
            String originAirportCode = (String) flightDetail.get("originAirportCode");
            String destinationAirportCode = (String) flightDetail.get("destinationAirportCode");

            // Debug the raw date objects before conversion
            Object departureTimeObj = flightDetail.get("departureTime");
            Object arrivalTimeObj = flightDetail.get("arrivalTime");
            log.debug("[MULTI_SEGMENT] Raw departureTime: type={}, value={}",
                    departureTimeObj != null ? departureTimeObj.getClass().getSimpleName() : "null", departureTimeObj);
            log.debug("[MULTI_SEGMENT] Raw arrivalTime: type={}, value={}",
                    arrivalTimeObj != null ? arrivalTimeObj.getClass().getSimpleName() : "null", arrivalTimeObj);

            // Handle LocalDateTime conversion - may come as ArrayList from JSON
            // deserialization
            LocalDateTime departureTime = convertToLocalDateTime(departureTimeObj);
            LocalDateTime arrivalTime = convertToLocalDateTime(arrivalTimeObj);

            // Get seat selections for this flight and convert to passenger index -> seat
            // code map
            // Use flight ID (UUID) as the key, not flight index
            List<String> flightSeats = selectedSeatsByFlight != null
                    ? selectedSeatsByFlight.getOrDefault(flightId.toString(), Collections.emptyList())
                    : Collections.emptyList();

            // Build pricing map from frontend data for this flight
            Map<Integer, Double> passengerPricingMap = new HashMap<>();
            if (seatPricingByFlight != null && flightIndex < seatPricingByFlight.size()) {
                List<Map<String, Object>> flightPricing = seatPricingByFlight.get(flightIndex);
                log.info("[MULTI_SEGMENT] Found {} pricing entries for flight {}",
                        flightPricing.size(), flightIndex);

                for (int passengerIndex = 0; passengerIndex < flightPricing.size()
                        && passengerIndex < passengerCount; passengerIndex++) {
                    Map<String, Object> seatPricing = flightPricing.get(passengerIndex);
                    if (seatPricing.containsKey("farePrice")) {
                        Object priceObj = seatPricing.get("farePrice");
                        double price = 0.0;
                        if (priceObj instanceof Number) {
                            price = ((Number) priceObj).doubleValue();
                        }
                        passengerPricingMap.put(passengerIndex, price);
                        log.debug("[MULTI_SEGMENT] Passenger {} price for flight {}: {}",
                                passengerIndex, flightIndex, price);
                    }
                }
            } else {
                log.warn("[MULTI_SEGMENT] No pricing information available for flight {}, using fallback", flightIndex);
                // Fallback to equal distribution if pricing data is missing
                double fallbackPrice = totalAmount / (passengerCount * flightIds.size());
                for (int i = 0; i < passengerCount; i++) {
                    passengerPricingMap.put(i, fallbackPrice);
                }
            }

            Map<Integer, String> seatMap = new HashMap<>();
            for (int i = 0; i < flightSeats.size() && i < passengerCount; i++) {
                seatMap.put(i, flightSeats.get(i));
            }

            log.info("[MULTI_SEGMENT] Processing flight index {}, ID: {}, seats: {}",
                    flightIndex, flightId, flightSeats.size());

            if (flightSeats.size() > 0) {
                log.info("[MULTI_SEGMENT] Found {} seats for flight {}: {}",
                        flightSeats.size(), flightId, flightSeats);
            } else {
                log.warn("[MULTI_SEGMENT] No seats found for flight {}, available keys: {}",
                        flightId, selectedSeatsByFlight != null ? selectedSeatsByFlight.keySet() : "null");
            }

            // Use utility method to create booking details for all passengers on this
            // segment
            // Ensure the booking code has the correct format with segment suffix
            String segmentBookingCode = BookingUtils.formatBookingCode(bookingReferenceForDisplay, flightIndex);

            log.info("[MULTI_SEGMENT] Creating booking details for segment {} with booking code {}", flightIndex,
                    segmentBookingCode);

            // Filter out passengers who already have a booking detail for this flight
            List<PassengerInfoDTO> validPassengers = new ArrayList<>();
            Map<Integer, Passenger> validPassengerEntities = new HashMap<>();
            Map<Integer, String> validSeatMap = new HashMap<>();
            Map<Integer, Double> validPricingMap = new HashMap<>();

            for (int passengerIndex = 0; passengerIndex < passengerInfosFromRequest.size(); passengerIndex++) {
                Passenger passenger = passengerEntities.get(passengerIndex);

                if (passenger != null && !hasDuplicateBookingDetail(allBookingDetails, flightId, passenger.getId())) {
                    validPassengers.add(passengerInfosFromRequest.get(passengerIndex));
                    validPassengerEntities.put(validPassengers.size() - 1, passenger);

                    if (seatMap.containsKey(passengerIndex)) {
                        validSeatMap.put(validPassengers.size() - 1, seatMap.get(passengerIndex));
                    }

                    if (passengerPricingMap.containsKey(passengerIndex)) {
                        validPricingMap.put(validPassengers.size() - 1, passengerPricingMap.get(passengerIndex));
                    }
                } else if (passenger != null) {
                    log.warn("[MULTI_SEGMENT] Skipping duplicate booking detail for passenger {} on flight {}",
                            passenger.getId(), flightId);
                }
            }

            // Only create booking details if we have valid passengers
            BookingUtils.BookingDetailsResult segmentResult;
            if (!validPassengers.isEmpty()) {
                segmentResult = BookingUtils.createBookingDetailsForSegmentWithActualPricing(
                        booking,
                        validPassengerEntities,
                        validPassengers,
                        flightId,
                        flightCode,
                        originAirportCode,
                        destinationAirportCode,
                        departureTime,
                        arrivalTime,
                        selectedFareNameFromRequest,
                        validSeatMap,
                        validPricingMap,
                        segmentBookingCode);
            } else {
                log.info("[MULTI_SEGMENT] No valid passengers for flight {}, skipping detail creation", flightId);
                segmentResult = new BookingUtils.BookingDetailsResult(Collections.emptyList(), 0.0);
            }

            // Add details and accumulate total
            allBookingDetails.addAll(segmentResult.getDetails());
            actualTotalAmount += segmentResult.getTotalAmount();

            log.debug("[MULTI_SEGMENT] Added {} booking details for flight segment {}, subtotal: {}",
                    segmentResult.getDetails().size(), flightIndex, segmentResult.getTotalAmount());
        }

        // Set the booking details and update the total amount
        booking.setBookingDetails(allBookingDetails);
        actualTotalAmount = Math.round(actualTotalAmount * 100.0) / 100.0;
        booking.setTotalAmount(actualTotalAmount);

        log.info("Service: Calculated actual total amount: {} (original: {}) for multi-segment booking {}",
                actualTotalAmount, totalAmount, bookingReferenceForDisplay);

        Booking savedBooking = bookingRepository.save(booking);
        log.info("Service: Pending multi-segment booking created successfully. DB ID: {}, Ref: {}, Details: {}",
                savedBooking.getId(), savedBooking.getBookingReference(), allBookingDetails.size());

        // Process baggage add-ons if provided
        processBaggageAddonsForBooking(savedBooking, baggageAddons);

        // Apply 10% tax to the total amount
        double totalWithTax = savedBooking.getTotalAmount() * 1.10;
        totalWithTax = Math.round(totalWithTax * 100.0) / 100.0;
        savedBooking.setTotalAmount(totalWithTax);

        log.info("Service: Applied 10% tax. Final total amount: {} for multi-segment booking {}",
                totalWithTax, bookingReferenceForDisplay);

        // Save booking again to persist updated total amount that includes baggage
        // costs and tax
        savedBooking = bookingRepository.save(savedBooking);

        // Create initial payment request
        try {
            CreatePaymentRequest paymentReq = new CreatePaymentRequest();
            paymentReq.setBookingReference(savedBooking.getBookingReference());
            paymentReq.setAmount(savedBooking.getTotalAmount());
            paymentReq.setPaymentMethod(paymentMethodFromRequest);
            paymentReq.setOrderDescription("Payment for booking: " + savedBooking.getBookingReference());

            Payment payment = paymentService.createPayment(paymentReq, userId);
            log.info("Service: Initial payment record created for booking {}, payment ID: {}",
                    savedBooking.getBookingReference(), payment.getId());
        } catch (Exception e) {
            log.error("Service: Failed to create payment record for booking {}", savedBooking.getBookingReference(), e);
            // Continue without throwing - payment can be created later
        }

        return savedBooking;
    }

    @Override
    public void completeSagaInitiationWithPaymentUrl(UUID sagaId, String bookingReferenceDisplay, Double totalAmount,
            String vnpayPaymentUrl, LocalDateTime paymentDeadline) {
        CompletableFuture<BookingInitiatedResponseDTO> sagaResultFuture = pendingSagaResponses.remove(sagaId);
        if (sagaResultFuture != null) {
            BookingInitiatedResponseDTO response = BookingInitiatedResponseDTO.builder()
                    .bookingReference(bookingReferenceDisplay)
                    .totalAmount(totalAmount)
                    .paymentStatus(PaymentStatus.PENDING)
                    .vnpayPaymentUrl(vnpayPaymentUrl)
                    .paymentDeadline(paymentDeadline)
                    .build();
            sagaResultFuture.complete(response);
            log.info(
                    "[SAGA_INIT_COMPLETE][SagaID:{}] Saga initiation completed, VNPAY URL provided for UserBookingRef: {}",
                    sagaId, bookingReferenceDisplay);
        } else {
            log.warn("[SAGA_INIT_WARN][SagaID:{}] No pending future found to complete for UserBookingRef: {}", sagaId,
                    bookingReferenceDisplay);
        }
    }

    @Override
    public void failSagaInitiationBeforePaymentUrl(UUID sagaId, String bookingReferenceDisplay, String errorMessage) {
        CompletableFuture<BookingInitiatedResponseDTO> sagaResultFuture = pendingSagaResponses.remove(sagaId);
        if (sagaResultFuture != null) {
            sagaResultFuture.completeExceptionally(new SagaProcessingException(
                    "Saga failed for booking " + bookingReferenceDisplay + ": " + errorMessage));
            log.error("[SAGA_INIT_FAIL][SagaID:{}] Saga initiation failed for UserBookingRef {}: {}", sagaId,
                    bookingReferenceDisplay, errorMessage);
        }
    }

    /**
     * Cancels an overdue pending booking.
     * Uses REQUIRES_NEW to ensure a new transaction is always started,
     * even if called from another transactional method.
     * Uses READ_COMMITTED isolation to allow reading data committed by other
     * transactions.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void cancelOverduePendingBooking(String bookingIdentifier) {
        log.warn("Service Scheduler: Attempting to cancel overdue pending booking: {}", bookingIdentifier);
        Optional<Booking> optionalBooking;
        try {
            UUID bookingId = UUID.fromString(bookingIdentifier);
            optionalBooking = bookingRepository.findById(bookingId);
        } catch (IllegalArgumentException e) {
            optionalBooking = bookingRepository.findByBookingReference(bookingIdentifier);
        }

        if (optionalBooking.isPresent()) {
            Booking booking = optionalBooking.get();
            if (booking.getStatus() == BookingStatus.PENDING_PAYMENT &&
                    booking.getPaymentDeadline() != null &&
                    booking.getPaymentDeadline().isBefore(LocalDateTime.now())) {

                log.info("Service Scheduler: Cancelling booking ID {}, Ref {}", booking.getId(),
                        booking.getBookingReference());
                booking.setStatus(BookingStatus.CANCELLED_NO_PAYMENT);

                Optional<Payment> bookingPaymentOpt = paymentRepository.findByBooking(booking);
                bookingPaymentOpt.ifPresent(payment -> {
                    if (payment.getPaymentType() == PaymentType.BOOKING_INITIAL
                            && payment.getStatus() == PaymentStatus.PENDING) {
                        payment.setStatus(PaymentStatus.EXPIRED);
                        paymentRepository.save(payment);
                        log.info("Service Scheduler: Payment ID {} for booking {} status set to EXPIRED.",
                                payment.getId(), booking.getBookingReference());
                    }
                });

                if (booking.getBookingDetails() != null) {
                    for (BookingDetail detail : booking.getBookingDetails()) {
                        detail.setStatus(BookingDetailStatus.CANCELLED);
                        try {
                            FsReleaseFareRequestDTO releaseRequest = FsReleaseFareRequestDTO.builder()
                                    .bookingReference(booking.getBookingReference())
                                    .fareName(detail.getSelectedFareName())
                                    .countToRelease(1)
                                    .reason("BOOKING_EXPIRED_NO_PAYMENT_SCHEDULER")
                                    .build();
                            flightClient.releaseFare(detail.getFlightId(), releaseRequest.getFareName(),
                                    releaseRequest);
                            log.info(
                                    "Service Scheduler: Fare inventory release requested for booking detail {}, flightId {}, fare {}",
                                    detail.getId(), detail.getFlightId(), detail.getSelectedFareName());
                        } catch (Exception e) {
                            log.error("Service Scheduler: Failed to request fare inventory release for detail {}: {}",
                                    detail.getId(), e.getMessage());
                        }
                    }
                }
                bookingRepository.save(booking);
                log.info("Service Scheduler: Booking {} (ID: {}) cancelled due to payment timeout.",
                        booking.getBookingReference(), booking.getId());

                // TODO: Gửi thông báo cho user
            } else {
                log.info(
                        "Service Scheduler: Booking {} (ID: {}) not cancelled. Status: {} or Payment Deadline {} not passed yet (Current time: {}).",
                        booking.getBookingReference(), booking.getId(), booking.getStatus(),
                        booking.getPaymentDeadline(), LocalDateTime.now());
            }
        } else {
            log.warn("Service Scheduler: Booking with identifier {} not found for cancellation.", bookingIdentifier);
        }
    }

    /**
     * Cancels a confirmed booking and adjusts loyalty points if applicable
     */
    @Override
    @Transactional
    public void cancelBooking(UUID bookingId, String reason) {
        try {
            Booking booking = bookingRepository.findById(bookingId)
                    .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));

            if (booking.getStatus() != BookingStatus.PAID) {
                throw new BadRequestException("Cannot cancel booking with status: " + booking.getStatus());
            }

            log.info("Cancelling booking {}: {}", bookingId, reason);

            // Adjust loyalty points if they were earned
            // if (booking.getPointsEarned() != null && booking.getPointsEarned() > 0) {
            // adjustLoyaltyPointsForCancellation(booking);
            // }

            // Cancel booking details
            if (booking.getBookingDetails() != null) {
                for (BookingDetail detail : booking.getBookingDetails()) {
                    detail.setStatus(BookingDetailStatus.CANCELLED);
                    try {
                        // Release fare inventory
                        FsReleaseFareRequestDTO releaseRequest = FsReleaseFareRequestDTO.builder()
                                .bookingReference(booking.getBookingReference())
                                .fareName(detail.getSelectedFareName())
                                .countToRelease(1)
                                .reason("BOOKING_CANCELLED_BY_USER")
                                .build();
                        flightClient.releaseFare(detail.getFlightId(), releaseRequest.getFareName(), releaseRequest);
                        log.info(
                                "Fare inventory release requested for cancelled booking detail {}, flightId {}, fare {}",
                                detail.getId(), detail.getFlightId(), detail.getSelectedFareName());
                    } catch (Exception e) {
                        log.error("Failed to request fare inventory release for detail {}: {}", detail.getId(),
                                e.getMessage());
                    }
                }
            }

            // Update booking status
            booking.setStatus(BookingStatus.CANCELLED);
            bookingRepository.save(booking);

            log.info("Booking {} cancelled successfully", bookingId);

        } catch (Exception e) {
            log.error("Error cancelling booking {}: {}", bookingId, e.getMessage());
            throw new BadRequestException("Failed to cancel booking: " + e.getMessage());
        }
    }

    /**
     * Updates the booking status after payment processing.
     * Uses REQUIRED propagation as this should be called within an existing
     * transaction.
     * Uses REPEATABLE_READ isolation to ensure consistent reads during the update.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.REPEATABLE_READ)
    public void updateBookingStatusAndDetailsPostPayment(String bookingReference,
            boolean paymentSuccessfulAndSeatsConfirmed, String vnpayTransactionIdIfSuccessful) {
        log.info(
                "Service: Updating booking status post-payment for bookingRef: {}. Overall success: {}, VNPAY TxnNo: {}",
                bookingReference, paymentSuccessfulAndSeatsConfirmed, vnpayTransactionIdIfSuccessful);

        Booking booking = bookingRepository.findByBookingReference(bookingReference)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", "reference", bookingReference));

        if (paymentSuccessfulAndSeatsConfirmed) {
            if (booking.getStatus() == BookingStatus.PENDING_PAYMENT) {
                booking.setStatus(BookingStatus.PAID);
                booking.getBookingDetails().forEach(detail -> detail.setStatus(BookingDetailStatus.BOOKED));
                log.info("Service: Booking {} (ID: {}) status updated to PAID.", booking.getBookingReference(),
                        booking.getId());

            } else {
                log.warn(
                        "Service: Booking {} (ID: {}) not in PENDING_PAYMENT state (current: {}). Update to PAID skipped by post-payment handler.",
                        booking.getBookingReference(), booking.getId(), booking.getStatus());
            }
        } else {
            if (booking.getStatus() == BookingStatus.PENDING_PAYMENT) {
                Payment primaryPayment = paymentRepository
                        .findByBookingAndPaymentType(booking, PaymentType.BOOKING_INITIAL)
                        .orElse(null);

                if (primaryPayment != null && primaryPayment.getStatus() == PaymentStatus.COMPLETED) {
                    booking.setStatus(BookingStatus.FAILED_TO_CONFIRM_SEATS);
                    primaryPayment.setStatus(PaymentStatus.REFUND_PENDING_MANUAL);
                } else {
                    booking.setStatus(BookingStatus.PAYMENT_FAILED);
                    if (primaryPayment != null && primaryPayment.getStatus() == PaymentStatus.PENDING) {
                        primaryPayment.setStatus(PaymentStatus.FAILED);
                    }
                }
                booking.getBookingDetails().forEach(detail -> detail.setStatus(BookingDetailStatus.CANCELLED));
                log.info("Service: Booking {} (ID: {}) status updated due to failure. Current: {}.",
                        booking.getBookingReference(), booking.getId(), booking.getStatus());
            } else {
                log.warn(
                        "Service: Booking {} (ID: {}) not in PENDING_PAYMENT state (current: {}). Update due to failure skipped.",
                        booking.getBookingReference(), booking.getId(), booking.getStatus());
            }
        }
        bookingRepository.save(booking);
    }

    /**
     * Retrieves detailed booking information by booking reference.
     * Uses readOnly=true for optimized read performance.
     * Uses READ_COMMITTED isolation for fast reads while ensuring data is
     * committed.
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public BookingFullDetailResponseDTO getBookingDetailsByReference(String bookingReference, UUID currentUserId,
            String currentUserRole) {
        log.info("Service: Fetching booking details for reference: {} by user: {}", bookingReference, currentUserId);
        if (bookingReference == null || bookingReference.isBlank()) {
            throw new BadRequestException("Booking reference cannot be blank.");
        }
        if (currentUserId == null || currentUserRole == null || currentUserRole.isBlank()) {
            throw new BadRequestException("User ID and Role are required for authorization check.");
        }

        Booking booking = bookingRepository.findByBookingReference(bookingReference)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", "reference", bookingReference));

        boolean isAdmin = "ADMIN".equalsIgnoreCase(currentUserRole);

        if (!isAdmin && !booking.getUserId().equals(currentUserId)) {
            log.warn("Service: Access denied for user {} (role: {}) trying to access booking {} owned by {}",
                    currentUserId, currentUserRole, bookingReference, booking.getUserId());
            throw new AccessDeniedException("You do not have permission to view this booking.");
        }

        return bookingMapper.toBookingFullDetailResponseDTO(booking);
    }

    /**
     * Retrieves paginated booking summaries for a user.
     * Uses readOnly=true for optimized read performance.
     * Uses READ_COMMITTED isolation for fast reads while ensuring data is
     * committed.
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public Page<BookingSummaryDTO> getUserBookings(UUID userId, Pageable pageable) {
        return getUserBookings(userId, null, pageable);
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public Page<BookingSummaryDTO> getUserBookings(UUID userId, String statusStr, Pageable pageable) {
        log.info("Service: Fetching bookings for user: {} with status: {} and pageable: {}", userId, statusStr,
                pageable);
        if (userId == null) {
            throw new BadRequestException("User ID cannot be null for fetching user bookings.");
        }

        Page<Booking> bookingPage;
        if (statusStr != null && !statusStr.isEmpty()) {
            try {
                BookingStatus status = BookingStatus.valueOf(statusStr);
                bookingPage = bookingRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, status, pageable);
            } catch (IllegalArgumentException e) {
                log.error("Service: Invalid booking status: {}", statusStr, e);
                throw new BadRequestException("Invalid booking status: " + statusStr);
            }
        } else {
            bookingPage = bookingRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        }

        return bookingPage.map(bookingMapper::toBookingSummaryDTO);
    }

    /**
     * Retrieves active vouchers for a user from the loyalty service.
     * Uses readOnly=true since this method only reads data.
     * No explicit transaction is needed as this is a proxy to another service.
     */
    @Override
    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public List<LsUserVoucherDTO> getActiveUserVouchers(UUID userId) {
        log.info("Service: Fetching active vouchers for user: {}", userId);
        if (userId == null) {
            throw new BadRequestException("User ID cannot be null for fetching active vouchers.");
        }

        // Use proxy endpoint to handle loyalty service errors gracefully
        try {
            ApiResponse<List<LsUserVoucherDTO>> response = loyaltyClient.getActiveUserVouchers(userId);
            if (response != null && response.getData() != null) {
                return response.getData();
            } else {
                log.warn("Service: Loyalty service returned empty or null response for user: {}", userId);
                return Collections.emptyList();
            }
        } catch (Exception e) {
            log.error("Service: Exception while fetching active vouchers for user {}: {}", userId, e.getMessage(), e);
            // Return empty list instead of throwing exception to handle gracefully
            log.warn("Service: Returning empty voucher list due to loyalty service error for user: {}", userId);
            return Collections.emptyList();
        }
    }

    /**
     * Handles post-payment workflow including event publishing.
     * Uses REQUIRED propagation to use the existing transaction if available.
     */
    @Override
    /**
     * Handles post-payment workflow including event publishing.
     * Uses REQUIRED propagation to use the existing transaction if available.
     * Improved exception handling and logging for better troubleshooting.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void handlePostPaymentWorkflow(String bookingReference) {
        try {
            log.info("[POST_PAYMENT_WORKFLOW] Starting post-payment workflow for booking: {}", bookingReference);

            Booking booking = bookingRepository.findByBookingReference(bookingReference)
                    .orElseThrow(() -> new ResourceNotFoundException("Booking", "reference", bookingReference));

            if (booking.getStatus() != BookingStatus.PAID) {
                log.warn("[POST_PAYMENT_WORKFLOW] Booking {} is not in PAID status, current status: {}",
                        bookingReference, booking.getStatus());
                return;
            }

            String transactionId = paymentRepository.findByBookingAndPaymentType(booking, PaymentType.BOOKING_INITIAL)
                    .map(Payment::getTransactionId)
                    .orElse("UNKNOWN");

            BookingCompletedEvent completedEvent = new BookingCompletedEvent(
                    this,
                    booking.getBookingReference(),
                    booking.getId(),
                    booking.getUserId(),
                    booking.getTotalAmount(),
                    transactionId);

            log.info("[POST_PAYMENT_WORKFLOW] Publishing booking completed event for booking: {}, userId: {}",
                    bookingReference, booking.getUserId());
            eventPublisher.publishEvent(completedEvent);
            log.info("[POST_PAYMENT_WORKFLOW] Post-payment workflow completed successfully for booking: {}",
                    bookingReference);
        } catch (ResourceNotFoundException e) {
            log.error("[POST_PAYMENT_WORKFLOW] Booking not found: {}", bookingReference, e);
            // Cannot continue without a valid booking
        } catch (Exception e) {
            log.error("[POST_PAYMENT_WORKFLOW] Error in post-payment workflow for booking: {}", bookingReference, e);
            // This should be handled by a retry mechanism or manual intervention
        }
    }

    /**
     * Calculate age category based on date of birth
     * Delegated to BookingUtils for reuse
     */
    private String getAgeCategory(LocalDate dateOfBirth) {
        return BookingUtils.getAgeCategory(dateOfBirth);
    }

    /**
     * Enhanced pricing calculation that considers age categories and seat
     * selections
     * Delegates to BookingUtils for core calculation while adding logging
     */
    private double calculatePassengerPrice(PassengerInfoDTO passenger, SeatSelectionDTO seatSelection,
            double baseFarePrice) {
        String ageCategory = getAgeCategory(passenger.getDateOfBirth());
        String selectedFareName = (seatSelection != null) ? seatSelection.getSelectedFareName() : null;

        // Calculate price using utility method
        double finalPrice = BookingUtils.calculatePassengerPrice(ageCategory, baseFarePrice, selectedFareName);

        // Add detailed logging
        if ("baby".equals(ageCategory)) {
            log.debug("Passenger {} is a baby, using fixed price: {}", passenger.getFirstName(),
                    BookingUtils.BABY_PRICE);
        } else if (seatSelection != null && seatSelection.getSelectedFareName() != null) {
            log.debug("Passenger {} ({}) selected seat {} with fare {}, calculated price: {}",
                    passenger.getFirstName(), ageCategory, seatSelection.getSeatCode(),
                    selectedFareName, finalPrice);
        } else {
            log.debug("Passenger {} ({}) has no seat selection, using base price: {}",
                    passenger.getFirstName(), ageCategory, finalPrice);
        }

        return finalPrice;
    }

    // Removed calculatePassengerSegmentPrice method - now using
    // BookingUtils.calculatePassengerPrice directly

    /**
     * Common method to process passenger data for both single and multi-segment
     * bookings
     * Helps reduce code duplication between booking flows
     *
     * @param passengers List of passenger information from request
     * @param userId     User ID making the booking
     * @return Map of passenger index to passenger entity
     */
    private Map<Integer, Passenger> processPassengers(List<PassengerInfoDTO> passengers, UUID userId) {
        Map<Integer, Passenger> passengerEntities = new HashMap<>();

        if (passengers == null || passengers.isEmpty()) {
            throw new BadRequestException("Passenger list cannot be empty for booking.");
        }

        for (int passengerIndex = 0; passengerIndex < passengers.size(); passengerIndex++) {
            PassengerInfoDTO passengerRequest = passengers.get(passengerIndex);
            Passenger passengerEntity;

            // Create passenger entity from DTO
            Passenger newPassengerEntity = passengerMapper.toPassengerEntity(passengerRequest);

            // Always create new passengers - frontend sends numeric IDs which aren't UUIDs
            // This simplifies the flow and avoids UUID conversion issues
            newPassengerEntity.setUserId(userId);
            passengerEntity = passengerRepository.save(newPassengerEntity);

            passengerEntities.put(passengerIndex, passengerEntity);

            // Log passenger information
            String ageCategory = getAgeCategory(passengerRequest.getDateOfBirth());
            log.debug("Processed passenger {}: {} {} ({}), category: {}",
                    passengerIndex,
                    passengerRequest.getFirstName(),
                    passengerRequest.getLastName(),
                    passengerEntity.getId(),
                    ageCategory);
        }

        return passengerEntities;
    }

    /**
     * Validates and maps seat selections to passenger indexes
     *
     * @param seatSelections            List of seat selections
     * @param passengerInfosFromRequest List of passenger infos
     * @return Map of passenger index to seat code
     */
    private Map<Integer, String> validateAndMapSeatSelections(
            List<SeatSelectionDTO> seatSelections,
            List<PassengerInfoDTO> passengerInfosFromRequest) {

        Map<Integer, String> seatSelectionMap = new HashMap<>();

        if (seatSelections == null || seatSelections.isEmpty()) {
            log.info("[SEAT_DEBUG] No seat selections to process");
            return seatSelectionMap;
        }

        log.info("[SEAT_DEBUG] Processing {} seat selections for {} passengers",
                seatSelections.size(), passengerInfosFromRequest.size());

        for (SeatSelectionDTO seatSelection : seatSelections) {
            if (seatSelection != null && seatSelection.getPassengerIndex() != null) {
                int passengerIndex = seatSelection.getPassengerIndex();

                // Validate passenger index
                if (passengerIndex < 0 || passengerIndex >= passengerInfosFromRequest.size()) {
                    throw new BadRequestException(
                            String.format("Invalid passenger index %d for seat %s. Must be between 0 and %d",
                                    passengerIndex, seatSelection.getSeatCode(), passengerInfosFromRequest.size() - 1));
                }

                // Check for duplicate passenger index
                if (seatSelectionMap.containsKey(passengerIndex)) {
                    throw new BadRequestException(
                            String.format("Duplicate seat selection for passenger index %d", passengerIndex));
                }

                seatSelectionMap.put(passengerIndex, seatSelection.getSeatCode());
                log.info("[SEAT_DEBUG] ✅ Mapped passenger[{}] -> seat {}",
                        passengerIndex, seatSelection.getSeatCode());
            }
        }

        log.info("[SEAT_DEBUG] Successfully mapped {} seats", seatSelectionMap.size());
        return seatSelectionMap;
    }

    /**
     * Helper method to convert Object (potentially ArrayList from JSON
     * deserialization)
     * to LocalDateTime. This handles the case where RabbitMQ JSON deserialization
     * converts LocalDateTime to ArrayList.
     */
    private LocalDateTime convertToLocalDateTime(Object dateTimeObj) {
        if (dateTimeObj == null) {
            return null;
        }

        if (dateTimeObj instanceof LocalDateTime) {
            return (LocalDateTime) dateTimeObj;
        }

        if (dateTimeObj instanceof List) {
            // JSON deserialization may convert LocalDateTime to [year, month, day, hour,
            // minute] or [year, month, day, hour, minute, second, nano]
            List<?> dateList = (List<?>) dateTimeObj;
            log.debug("Converting ArrayList to LocalDateTime: size={}, content={}", dateList.size(), dateList);

            if (dateList.size() >= 5) {
                try {
                    int year = ((Number) dateList.get(0)).intValue();
                    int month = ((Number) dateList.get(1)).intValue();
                    int day = ((Number) dateList.get(2)).intValue();
                    int hour = ((Number) dateList.get(3)).intValue();
                    int minute = ((Number) dateList.get(4)).intValue();
                    int second = dateList.size() > 5 ? ((Number) dateList.get(5)).intValue() : 0;
                    int nano = dateList.size() > 6 ? ((Number) dateList.get(6)).intValue() : 0;

                    LocalDateTime result = LocalDateTime.of(year, month, day, hour, minute, second, nano);
                    log.debug("Successfully converted ArrayList {} to LocalDateTime: {}", dateList, result);
                    return result;
                } catch (Exception e) {
                    log.error("Failed to convert ArrayList to LocalDateTime: {}", dateList, e);
                    throw new IllegalArgumentException("Invalid date format in flight details", e);
                }
            } else {
                log.error("ArrayList has insufficient elements for LocalDateTime conversion: size={}, content={}",
                        dateList.size(), dateList);
                log.warn(
                        "DEPRECATION WARNING: Received legacy date format as a List. Please switch to ISO 8601 String. Content: {}",
                        dateList);
                throw new IllegalArgumentException("Invalid date format: ArrayList has " + dateList.size()
                        + " elements, need at least 5. Received: " + dateList);
            }
        }

        if (dateTimeObj instanceof String) {
            // Handle string format if needed
            try {
                return LocalDateTime.parse((String) dateTimeObj);
            } catch (Exception e) {
                log.error("Failed to parse LocalDateTime from string: {}", dateTimeObj, e);
                throw new IllegalArgumentException("Invalid date format in flight details", e);
            }
        }

        throw new IllegalArgumentException("Unsupported date format: " + dateTimeObj.getClass().getSimpleName());
    }

    // ===============================
    // Loyalty Integration Methods
    // ===============================
    // Note: These methods require loyalty service DTOs that may not be available
    // yet

    /*
     * /**
     * Validates a voucher for booking usage
     */
    /*
     *
     * public LsValidateVoucherResponseDTO
     * validateVoucherForBooking(CreateBookingRequestDTO request) {
     * try {
     * LsValidateVoucherRequestDTO voucherRequest =
     * LsValidateVoucherRequestDTO.builder()
     * .voucherCode(request.getVoucherCode())
     * .userId(request.getUserId())
     * .bookingAmount(request.getTotalAmount())
     * .flightCode(request.getFlightId() != null ? request.getFlightId().toString()
     * : "")
     * .build();
     *
     * ApiResponse<LsValidateVoucherResponseDTO> response =
     * loyaltyClient.validateVoucher(voucherRequest);
     *
     * if (response.isSuccess()) {
     * return response.getData();
     * } else {
     * return LsValidateVoucherResponseDTO.builder()
     * .valid(false)
     * .errorMessage(response.getMessage())
     * .build();
     * }
     * } catch (Exception e) {
     * log.error("Error validating voucher: {}", e.getMessage());
     * return LsValidateVoucherResponseDTO.builder()
     * .valid(false)
     * .errorMessage("Voucher validation service unavailable")
     * .build();
     * }
     * }
     * /*
     * /**
     * Estimates points to be earned based on user membership tier and amount
     */
    /*
     *
     * public Integer estimatePointsToEarn(UUID userId, Double amount) {
     * try {
     * ApiResponse<LsMembershipDTO> membershipResponse =
     * loyaltyClient.getMembership(userId);
     * if (membershipResponse.isSuccess()) {
     * LsMembershipDTO membership = membershipResponse.getData();
     * // Calculate points based on tier (these rates should match loyalty service)
     * double rate = switch (membership.getTier()) {
     * case "GOLD" -> 0.015; // 1.5%
     * case "PLATINUM" -> 0.02; // 2%
     * default -> 0.01; // 1% for SILVER
     * };
     * return (int) (amount * rate);
     * }
     * } catch (Exception e) {
     * log.warn("Could not estimate points for user {}: {}", userId,
     * e.getMessage());
     * }
     * // Default estimation for SILVER tier
     * return (int) (amount * 0.01);
     * }
     */

    /*
     * /**
     * Creates a booking with loyalty information included
     */
    /*
     *
     * public BookingInitiatedResponseDTO
     * createBookingWithLoyaltyInfo(CreateBookingRequestDTO request) {
     * try {
     * // Validate voucher if provided
     * Double voucherDiscount = 0.0;
     * String voucherValidationMessage = null;
     *
     * if (request.getVoucherCode() != null &&
     * !request.getVoucherCode().trim().isEmpty()) {
     * LsValidateVoucherResponseDTO voucherValidation =
     * validateVoucherForBooking(request);
     * if (voucherValidation.isValid()) {
     * voucherDiscount = voucherValidation.getDiscountAmount();
     * voucherValidationMessage = "Voucher applied successfully";
     * } else {
     * throw new BookingValidationException("Invalid voucher: " +
     * voucherValidation.getErrorMessage());
     * }
     * }
     *
     * // Calculate final amount
     * Double originalAmount = request.getTotalAmount();
     * Double finalAmount = originalAmount - voucherDiscount;
     *
     * // Estimate points to be earned
     * Integer estimatedPoints = estimatePointsToEarn(request.getUserId(),
     * finalAmount);
     *
     * return BookingInitiatedResponseDTO.builder()
     * .status("PENDING_PAYMENT")
     * .originalAmount(originalAmount)
     * .appliedVoucherCode(request.getVoucherCode())
     * .voucherDiscountAmount(voucherDiscount)
     * .finalAmount(finalAmount)
     * .totalAmount(finalAmount)
     * .estimatedPointsToEarn(estimatedPoints)
     * .loyaltyMessage(voucherValidationMessage)
     * .build();
     *
     * } catch (Exception e) {
     * log.error("Error creating booking with loyalty info: {}", e.getMessage());
     * throw new BookingValidationException("Failed to create booking: " +
     * e.getMessage());
     * }
     * }
     */

    /*
     * /**
     * Awards loyalty points after successful payment
     */
    /*
     *
     * public void awardLoyaltyPointsForBooking(Booking booking) {
     * try {
     * LsEarnPointsRequestDTO pointsRequest = LsEarnPointsRequestDTO.builder()
     * .userId(booking.getUserId())
     * .bookingReference(booking.getBookingReference())
     * .amountSpent(booking.getTotalAmount())
     * .source("BOOKING_PAYMENT")
     * .build();
     *
     * ApiResponse<LsEarnPointsResponseDTO> response =
     * loyaltyClient.earnPoints(pointsRequest);
     *
     * if (response.isSuccess()) {
     * // Update booking with loyalty information
     * booking.setPointsEarned(response.getData().getPointsEarnedThisTransaction().
     * intValue());
     * booking.setLoyaltyTransactionId(generateLoyaltyTransactionId(booking));
     * bookingRepository.save(booking);
     *
     * log.info("Loyalty points awarded for booking {}: {} points",
     * booking.getId(), response.getData().getPointsEarnedThisTransaction());
     * } else {
     * log.warn("Failed to award loyalty points for booking {}: {}",
     * booking.getId(), response.getMessage());
     * }
     *
     * } catch (Exception e) {
     * log.error("Error awarding loyalty points for booking {}: {}",
     * booking.getId(), e.getMessage());
     * }
     * }
     */

    /*
     * /**
     * Adjusts loyalty points for cancelled bookings
     */
    /*
     * public void adjustLoyaltyPointsForCancellation(Booking booking) {
     * try {
     * ApiResponse<String> response =
     * loyaltyClient.adjustPoints(booking.getBookingReference());
     *
     * if (response.isSuccess()) {
     * log.info("Loyalty points adjusted for cancelled booking {}: {}",
     * booking.getId(), response.getData());
     * } else {
     * log.warn("Failed to adjust loyalty points for cancelled booking {}: {}",
     * booking.getId(), response.getMessage());
     * }
     *
     * } catch (Exception e) {
     * log.error("Error adjusting loyalty points for cancelled booking {}: {}",
     * booking.getId(), e.getMessage());
     * // Don't fail the cancellation if loyalty adjustment fails
     * }
     * }
     */

    /**
     * Checks if a booking detail already exists for a passenger on a specific
     * flight
     * This prevents duplicate booking details being created in multi-segment
     * bookings
     */
    private boolean hasDuplicateBookingDetail(List<BookingDetail> existingDetails, UUID flightId, UUID passengerId) {
        if (existingDetails == null || existingDetails.isEmpty()) {
            return false;
        }

        boolean hasDuplicate = existingDetails.stream()
                .anyMatch(detail -> {
                    boolean isMatch = detail.getFlightId().equals(flightId) &&
                            detail.getPassenger().getId().equals(passengerId);

                    if (isMatch) {
                        log.debug("Found duplicate booking detail: passenger={}, flight={}, existing booking code={}",
                                passengerId, flightId, detail.getBookingCode());
                    }

                    return isMatch;
                });

        if (hasDuplicate) {
            log.info("Preventing duplicate booking detail creation for passenger {} on flight {}",
                    passengerId, flightId);
        }

        return hasDuplicate;
    }

    /**
     * Processes baggage add-ons for a booking during creation
     */
    private void processBaggageAddonsForBooking(Booking booking, List<BaggageAddonRequestDTO> baggageAddonRequests) {
        if (baggageAddonRequests != null && !baggageAddonRequests.isEmpty()) {
            log.info("Processing {} baggage add-ons for booking {}", baggageAddonRequests.size(),
                    booking.getBookingReference());

            try {
                // Create baggage add-on entities using the service method
                List<BaggageAddon> baggageAddons = baggageAddonRequests.stream()
                        .map(dto -> createBaggageAddonEntity(dto, booking, false)) // false = not post-booking
                        .collect(Collectors.toList());

                // Set baggage add-ons to booking
                booking.setBaggageAddons(baggageAddons);

                // Calculate total baggage cost
                double totalBaggageCost = baggageAddons.stream()
                        .mapToDouble(BaggageAddon::getPrice)
                        .sum();

                // Update booking total amount to include baggage costs
                double originalAmount = booking.getTotalAmount();
                booking.setTotalAmount(originalAmount + totalBaggageCost);

                log.info(
                        "Successfully processed {} baggage add-ons for booking {} with total baggage cost: {}. Updated total amount from {} to {}",
                        baggageAddons.size(), booking.getBookingReference(), totalBaggageCost, originalAmount,
                        booking.getTotalAmount());

            } catch (Exception e) {
                log.error("Failed to process baggage add-ons for booking {}: {}", booking.getBookingReference(),
                        e.getMessage(), e);
                throw new BadRequestException("Failed to process baggage add-ons: " + e.getMessage());
            }
        }
    }

    /**
     * Creates a baggage addon entity from DTO
     */
    private BaggageAddon createBaggageAddonEntity(BaggageAddonRequestDTO dto, Booking booking, boolean isPostBooking) {
        return BaggageAddon.builder()
                .booking(booking)
                .passengerIndex(dto.getPassengerIndex())
                .baggageWeight(dto.getWeight())
                .price(dto.getPrice())
                .flightId(dto.getFlightId())
                .type(BaggageAddonType.valueOf(dto.getType()))
                .purchaseTime(LocalDateTime.now())
                .isPostBooking(isPostBooking)
                .build();
    }

    /*
     * /**
     * Generates a unique loyalty transaction ID
     */
    private String generateLoyaltyTransactionId(Booking booking) {
        return "LOYALTY_" + booking.getBookingReference() + "_" + System.currentTimeMillis();
    }

    /**
     * Records saga timeouts for monitoring
     */
    private void recordSagaTimeout(UUID sagaId, String bookingReferenceForUser) {
        log.warn("[SAGA_TIMEOUT][SagaID:{}] Saga timeout recorded for booking reference: {}",
                sagaId, bookingReferenceForUser);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getUserBookingStatistics(UUID userId) {
        log.info("Service: Fetching booking statistics for user: {}", userId);
        if (userId == null) {
            throw new BadRequestException("User ID cannot be null for fetching booking statistics.");
        }

        try {
            Map<String, Object> statistics = new HashMap<>();

            // Get counts for each status
            try {
                // Only count relevant bookings (exclude DRAFT_SELECTION)
                int totalExcludingDraft = bookingRepository.countByUserId(userId);
                int draft = bookingRepository.countByUserIdAndStatus(userId, BookingStatus.DRAFT_SELECTION);
                statistics.put("total", totalExcludingDraft - draft);
                log.debug("Service: Total bookings count (excluding drafts) for user {}: {}", userId,
                        statistics.get("total"));
            } catch (Exception e) {
                log.error("Service: Error counting total bookings for user {}: {}", userId, e.getMessage());
                statistics.put("total", 0);
            }

            try {
                statistics.put("draft",
                        bookingRepository.countByUserIdAndStatus(userId, BookingStatus.DRAFT_SELECTION));
            } catch (Exception e) {
                log.error("Service: Error counting DRAFT_SELECTION bookings for user {}: {}", userId, e.getMessage());
                statistics.put("draft", 0);
            }

            try {
                statistics.put("pendingPayment",
                        bookingRepository.countByUserIdAndStatus(userId, BookingStatus.PENDING_PAYMENT));
            } catch (Exception e) {
                log.error("Service: Error counting PENDING_PAYMENT bookings for user {}: {}", userId, e.getMessage());
                statistics.put("pendingPayment", 0);
            }

            try {
                statistics.put("paid", bookingRepository.countByUserIdAndStatus(userId, BookingStatus.PAID));
            } catch (Exception e) {
                log.error("Service: Error counting PAID bookings for user {}: {}", userId, e.getMessage());
                statistics.put("paid", 0);
            }

            try {
                statistics.put("completed", bookingRepository.countByUserIdAndStatus(userId, BookingStatus.COMPLETED));
            } catch (Exception e) {
                log.error("Service: Error counting COMPLETED bookings for user {}: {}", userId, e.getMessage());
                statistics.put("completed", 0);
            }

            try {
                int cancelled = bookingRepository.countByUserIdAndStatus(userId, BookingStatus.CANCELLED);
                int partiallyCancelled = bookingRepository.countByUserIdAndStatus(userId,
                        BookingStatus.PARTIALLY_CANCELLED);
                statistics.put("cancelled", cancelled + partiallyCancelled);
            } catch (Exception e) {
                log.error("Service: Error counting CANCELLED bookings for user {}: {}", userId, e.getMessage());
                statistics.put("cancelled", 0);
            }

            try {
                statistics.put("cancelledNoPayment",
                        bookingRepository.countByUserIdAndStatus(userId, BookingStatus.CANCELLED_NO_PAYMENT));
            } catch (Exception e) {
                log.error("Service: Error counting CANCELLED_NO_PAYMENT bookings for user {}: {}", userId,
                        e.getMessage());
                statistics.put("cancelledNoPayment", 0);
            }

            try {
                statistics.put("failed",
                        bookingRepository.countByUserIdAndStatus(userId, BookingStatus.PAYMENT_FAILED));
            } catch (Exception e) {
                log.error("Service: Error counting PAYMENT_FAILED bookings for user {}: {}", userId, e.getMessage());
                statistics.put("failed", 0);
            }

            // Add a new category for saga step errors (such as FAILED_TO_CONFIRM_SEATS)
            try {
                int sagaErrors = bookingRepository.countByUserIdAndStatus(userId,
                        BookingStatus.FAILED_TO_CONFIRM_SEATS);
                // Add any other saga error statuses here when they're added to the enum
                statistics.put("sagaErrors", sagaErrors);
            } catch (Exception e) {
                log.error("Service: Error counting saga error bookings for user {}: {}", userId, e.getMessage());
                statistics.put("sagaErrors", 0);
            }

            // Get total value of PAID and COMPLETED bookings
            try {
                Double totalValue = bookingRepository.sumTotalAmountByUserIdAndStatusInPaidOrCompleted(userId);
                statistics.put("totalValue", totalValue != null ? totalValue : 0.0);
                log.debug("Service: Total value of bookings for user {}: {}", userId, statistics.get("totalValue"));
            } catch (Exception e) {
                log.error("Service: Error calculating total booking value for user {}: {}", userId, e.getMessage(), e);
                statistics.put("totalValue", 0.0);
            }

            log.info("Service: Successfully collected booking statistics for user {}", userId);
            return statistics;
        } catch (Exception e) {
            log.error("Service: Unexpected error fetching booking statistics for user {}: {}", userId, e.getMessage(),
                    e);
            throw new RuntimeException("Failed to fetch booking statistics: " + e.getMessage(), e);
        }
    }

    @Override
    public Page<BookingSummaryDTO> getAllBookingsForAdmin(
            Pageable pageable,
            String status,
            String searchTerm,
            UUID userId,
            String flightCode,
            Double totalAmountMin,
            Double totalAmountMax,
            String dateFrom,
            String dateTo) {

        log.info(
                "Fetching all bookings for admin with filters - status: {}, searchTerm: {}, userId: {}, flightCode: {}",
                status, searchTerm, userId, flightCode);

        try {
            // 1. Chuẩn bị các tham số
            BookingStatus bookingStatus = StringUtils.hasText(status) ? BookingStatus.valueOf(status.toUpperCase())
                    : null;
            LocalDateTime startDate = StringUtils.hasText(dateFrom) ? LocalDate.parse(dateFrom).atStartOfDay() : null;
            LocalDateTime endDate = StringUtils.hasText(dateTo) ? LocalDate.parse(dateTo).atTime(23, 59, 59) : null;

            // 2. Tạo đối tượng Specification từ các bộ lọc
            Specification<Booking> spec = BookingSpecifications.withFilters(
                    bookingStatus, searchTerm, userId, flightCode,
                    totalAmountMin, totalAmountMax, startDate, endDate);

            // 3. Gọi phương thức findAll mới từ repository
            Page<Booking> bookings = bookingRepository.findAll(spec, pageable);

            // 4. Ánh xạ kết quả sang DTO (đây là phần bạn yêu cầu)
            return bookings.map(booking -> {
                try {
                    return bookingMapper.toBookingSummaryDTO(booking);
                } catch (Exception e) {
                    // Ghi log lỗi mapping cho một booking cụ thể mà không làm hỏng toàn bộ request
                    log.error("Error mapping booking {} to summary DTO: {}", booking.getBookingReference(),
                            e.getMessage());
                    // Trả về một đối tượng DTO dự phòng để không làm gián đoạn luồng dữ liệu
                    return createFallbackBookingSummary(booking);
                }
            });

        } catch (IllegalArgumentException e) {
            // Bắt lỗi khi parse status hoặc date không hợp lệ
            log.error("Invalid argument for admin booking search: {}", e.getMessage(), e);
            throw new RuntimeException("Invalid filter parameter provided.", e);
        } catch (Exception e) {
            // Bắt các lỗi chung khác
            log.error("Error fetching bookings for admin: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch bookings due to an unexpected error.", e);
        }
    }

    @Override
    public BookingFullDetailResponseDTO getBookingDetailsByReferenceForAdmin(String bookingReference) {
        log.info("Admin fetching booking details for: {}", bookingReference);

        try {
            Booking booking = bookingRepository.findByBookingReference(bookingReference)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Booking not found with reference: " + bookingReference));

            return bookingMapper.toBookingFullDetailResponseDTO(booking);
        } catch (Exception e) {
            log.error("Error fetching booking details for admin: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch booking details", e);
        }
    }

    @Override
    public Map<String, Object> getBookingStatisticsForAdmin(String dateFrom, String dateTo) {
        log.info("Fetching booking statistics for admin from {} to {}", dateFrom, dateTo);

        try {
            Map<String, Object> statistics = new HashMap<>();

            LocalDateTime startDate = StringUtils.hasText(dateFrom) ? LocalDate.parse(dateFrom).atStartOfDay()
                    : LocalDateTime.now().minusDays(30);
            LocalDateTime endDate = StringUtils.hasText(dateTo) ? LocalDate.parse(dateTo).atTime(23, 59, 59)
                    : LocalDateTime.now();

            long totalBookings = bookingRepository.countByCreatedAtBetween(startDate, endDate);
            statistics.put("total", totalBookings);

            for (BookingStatus status : BookingStatus.values()) {
                long count = bookingRepository.countByStatusAndCreatedAtBetween(status, startDate, endDate);
                statistics.put(status.name().toLowerCase(), count);
            }

            // Revenue calculations
            Double totalRevenue = bookingRepository.sumTotalAmountByStatusInAndCreatedAtBetween(
                    Arrays.asList(BookingStatus.PAID, BookingStatus.COMPLETED), startDate, endDate);
            statistics.put("totalRevenue", totalRevenue != null ? totalRevenue : 0.0);

            // Conversion rate (completed vs total)
            long completedBookings = bookingRepository.countByStatusAndCreatedAtBetween(BookingStatus.COMPLETED,
                    startDate, endDate);
            double conversionRate = totalBookings > 0 ? (double) completedBookings / totalBookings * 100 : 0.0;
            statistics.put("conversionRate", Math.round(conversionRate * 100.0) / 100.0);

            // Average booking value
            double averageBookingValue = totalRevenue != null && completedBookings > 0
                    ? totalRevenue / completedBookings
                    : 0.0;
            statistics.put("averageBookingValue", Math.round(averageBookingValue * 100.0) / 100.0);

            log.info("Successfully calculated admin statistics: total={}, revenue={}", totalBookings, totalRevenue);
            return statistics;
        } catch (Exception e) {
            log.error("Error fetching booking statistics for admin: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch booking statistics", e);
        }
    }

    @Override
    public Map<String, Object> getDailyRevenueForAdmin(String dateFrom, String dateTo) {
        log.info("Fetching daily revenue for admin from {} to {}", dateFrom, dateTo);

        try {
            Map<String, Object> revenueData = new HashMap<>();

            LocalDateTime startDate = StringUtils.hasText(dateFrom) ? LocalDate.parse(dateFrom).atStartOfDay()
                    : LocalDateTime.now().minusDays(30);
            LocalDateTime endDate = StringUtils.hasText(dateTo) ? LocalDate.parse(dateTo).atTime(23, 59, 59)
                    : LocalDateTime.now();

            // Get daily revenue data (this would require a custom repository method)
            List<BookingStatus> paidStatuses = Arrays.asList(BookingStatus.PAID, BookingStatus.COMPLETED);
            List<Map<String, Object>> dailyRevenue = bookingRepository.getDailyRevenueBetween(startDate, endDate,
                    paidStatuses);
            revenueData.put("dailyRevenue", dailyRevenue);

            // Calculate totals
            double totalRevenue = dailyRevenue.stream()
                    .mapToDouble(day -> ((Number) day.getOrDefault("revenue", 0)).doubleValue())
                    .sum();
            revenueData.put("totalRevenue", totalRevenue);

            long totalBookings = dailyRevenue.stream()
                    .mapToLong(day -> ((Number) day.getOrDefault("bookings", 0)).longValue())
                    .sum();
            revenueData.put("totalBookings", totalBookings);

            return revenueData;
        } catch (Exception e) {
            log.error("Error fetching daily revenue for admin: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch daily revenue", e);
        }
    }

    @Override
    public Map<String, Object> getTopRoutesForAdmin(String dateFrom, String dateTo, int limit) {
        log.info("Fetching top routes for admin from {} to {} with limit {}", dateFrom, dateTo, limit);

        try {
            Map<String, Object> routeData = new HashMap<>();

            // Date range handling
            LocalDateTime startDate = StringUtils.hasText(dateFrom) ? LocalDate.parse(dateFrom).atStartOfDay()
                    : LocalDateTime.now().minusDays(30);
            LocalDateTime endDate = StringUtils.hasText(dateTo) ? LocalDate.parse(dateTo).atTime(23, 59, 59)
                    : LocalDateTime.now();

            // Get top routes data (this would require a custom repository method)
            List<BookingStatus> paidStatuses = Arrays.asList(BookingStatus.PAID, BookingStatus.COMPLETED);
            List<Map<String, Object>> topRoutes = bookingRepository.getTopRoutesBetween(startDate, endDate,
                    paidStatuses, limit);
            routeData.put("topRoutes", topRoutes);

            return routeData;
        } catch (Exception e) {
            log.error("Error fetching top routes for admin: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch top routes", e);
        }
    }

    @Override
    @Transactional
    public void updateBookingStatusForAdmin(String bookingReference, String newStatus, String reason) {
        log.info("Admin updating booking {} status to {} with reason: {}", bookingReference, newStatus, reason);

        try {
            Booking booking = bookingRepository.findByBookingReference(bookingReference)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Booking not found with reference: " + bookingReference));

            BookingStatus oldStatus = booking.getStatus();
            BookingStatus status = BookingStatus.valueOf(newStatus.toUpperCase());

            booking.setStatus(status);
            booking.setUpdatedAt(LocalDateTime.now());

            // Add audit trail (if needed)
            // You could create an audit log entry here

            bookingRepository.save(booking);

            log.info("Successfully updated booking {} status from {} to {}",
                    bookingReference, oldStatus, status);
        } catch (Exception e) {
            log.error("Error updating booking status for admin: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to update booking status", e);
        }
    }

    @Override
    public String exportBookingsForAdmin(String status, String dateFrom, String dateTo, String format) {
        log.info("Admin exporting bookings - status: {}, from: {}, to: {}, format: {}", status, dateFrom, dateTo,
                format);

        try {
            // This is a placeholder implementation
            // In a real system, you would generate the export file and return a download
            // URL

            String fileName = String.format("bookings_export_%s.%s",
                    LocalDateTime.now().toString().replaceAll(":", "-"), format);

            // Simulate export process
            log.info("Export initiated for file: {}", fileName);

            // Return a placeholder URL
            return "/api/v1/admin/bookings/downloads/" + fileName;
        } catch (Exception e) {
            log.error("Error exporting bookings for admin: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to export bookings", e);
        }
    }

    // Helper method for fallback booking summary
    private BookingSummaryDTO createFallbackBookingSummary(Booking booking) {
        BookingSummaryDTO summary = new BookingSummaryDTO();

        // Basic booking information
        summary.setId(booking.getId());
        summary.setBookingReference(booking.getBookingReference());
        summary.setStatus(booking.getStatus());
        summary.setCreatedAt(booking.getCreatedAt());
        summary.setUpdatedAt(booking.getUpdatedAt());

        if (booking.getCreatedAt() != null) {
            summary.setBookingDate(booking.getCreatedAt().toLocalDate());
        }

        // User information
        summary.setUserId(booking.getUserId());

        // Financial information
        summary.setTotalAmount(booking.getTotalAmount());
        summary.setOriginalAmount(booking.getOriginalAmount());
        summary.setVoucherDiscountAmount(booking.getVoucherDiscountAmount());
        summary.setAppliedVoucherCode(booking.getAppliedVoucherCode());

        // Timing
        summary.setPaymentDeadline(booking.getPaymentDeadline());

        // Additional metadata
        summary.setPointsEarned(booking.getPointsEarned());
        summary.setBookingType(booking.getBookingDetails() != null && booking.getBookingDetails().size() > 1
                ? "MULTI_SEGMENT"
                : "SINGLE_SEGMENT");

        // Default empty flight summaries
        summary.setFlightSummaries(new ArrayList<>());
        summary.setPassengerCount(0);

        return summary;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getEnhancedBookingStatisticsForAdmin(String dateFrom, String dateTo) {
        log.info("Fetching enhanced booking statistics for admin from {} to {}", dateFrom, dateTo);

        LocalDateTime startDate = parseDate(dateFrom, true);
        LocalDateTime endDate = parseDate(dateTo, false);

        Map<String, Object> statistics = new HashMap<>();

        try {
            // Basic booking counts
            Long totalBookings = bookingRepository.countBookingsByStatusAndDateRange(null, startDate, endDate);
            Long completedBookings = bookingRepository.countBookingsByStatusAndDateRange(BookingStatus.COMPLETED,
                    startDate, endDate);
            Long paidBookings = bookingRepository.countPaidBookingsByDateRange(startDate, endDate);
            Long pendingPaymentBookings = bookingRepository
                    .countBookingsByStatusAndDateRange(BookingStatus.PENDING_PAYMENT, startDate, endDate);
            Long cancelledBookings = bookingRepository.countBookingsByStatusAndDateRange(BookingStatus.CANCELLED,
                    startDate, endDate);
            Long cancelledNoPaymentBookings = bookingRepository
                    .countBookingsByStatusAndDateRange(BookingStatus.CANCELLED_NO_PAYMENT, startDate, endDate);

            // Revenue calculations
            List<BookingStatus> revenueStatuses = Arrays.asList(BookingStatus.PAID, BookingStatus.COMPLETED);
            Double totalRevenue = bookingRepository.sumTotalAmountByStatusInAndCreatedAtBetween(revenueStatuses,
                    startDate, endDate);
            Double paymentBasedRevenue = paymentRepository.calculateTotalRevenueFromPayments(startDate, endDate);

            // Conversion rate calculation
            Double conversionRate = 0.0;
            if (totalBookings > 0) {
                Long successfulBookings = completedBookings + paidBookings;
                conversionRate = (successfulBookings.doubleValue() / totalBookings.doubleValue()) * 100;
            }

            // Populate statistics map
            statistics.put("total", totalBookings);
            statistics.put("completed", completedBookings);
            statistics.put("paid", paidBookings);
            statistics.put("pending_payment", pendingPaymentBookings);
            statistics.put("cancelled", cancelledBookings);
            statistics.put("cancelled_no_payment", cancelledNoPaymentBookings);
            statistics.put("totalRevenue", totalRevenue != null ? totalRevenue : 0.0);
            statistics.put("paymentBasedRevenue", paymentBasedRevenue != null ? paymentBasedRevenue : 0.0);
            statistics.put("conversionRate", Math.round(conversionRate * 100.0) / 100.0);

            // Payment statistics
            Long totalPayments = paymentRepository.countSuccessfulPaymentsByDateRange(startDate, endDate);
            statistics.put("totalPayments", totalPayments);

            log.info("Enhanced booking statistics calculated successfully: {}", statistics);
            return statistics;

        } catch (Exception e) {
            log.error("Error calculating enhanced booking statistics: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to calculate enhanced booking statistics", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getRevenueFromPayments(String dateFrom, String dateTo) {
        log.info("Fetching payment-based revenue from {} to {}", dateFrom, dateTo);

        LocalDateTime startDate = parseDate(dateFrom, true);
        LocalDateTime endDate = parseDate(dateTo, false);

        Map<String, Object> revenueData = new HashMap<>();

        try {
            // Total revenue from successful payments
            Double totalRevenue = paymentRepository.calculateTotalRevenueFromPayments(startDate, endDate);
            Long totalPayments = paymentRepository.countSuccessfulPaymentsByDateRange(startDate, endDate);

            // Daily revenue breakdown
            List<Object[]> dailyRevenueData;
            if (startDate != null && endDate != null) {
                dailyRevenueData = paymentRepository.getDailyRevenueFromPaymentsBetween(PaymentStatus.COMPLETED,
                        startDate, endDate);
            } else {
                dailyRevenueData = paymentRepository.getDailyRevenueFromAllSuccessfulPayments(PaymentStatus.COMPLETED);
                // Filter by date if needed
                if (startDate != null || endDate != null) {
                    // Filter in memory if only one date is provided
                    final LocalDateTime finalStartDate = startDate;
                    final LocalDateTime finalEndDate = endDate;
                    dailyRevenueData = dailyRevenueData.stream()
                            .filter(row -> {
                                try {
                                    LocalDateTime rowDate = LocalDateTime.parse(row[0].toString() + "T00:00:00");
                                    return (finalStartDate == null || !rowDate.isBefore(finalStartDate)) &&
                                            (finalEndDate == null || !rowDate.isAfter(finalEndDate));
                                } catch (Exception e) {
                                    return true; // Include if parsing fails
                                }
                            })
                            .collect(Collectors.toList());
                }
            }
            List<Map<String, Object>> dailyRevenue = new ArrayList<>();

            for (Object[] row : dailyRevenueData) {
                Map<String, Object> dailyEntry = new HashMap<>();
                dailyEntry.put("date", row[0].toString());
                dailyEntry.put("revenue", row[1]);
                dailyRevenue.add(dailyEntry);
            }

            // Average payment amount
            Double averagePayment = 0.0;
            if (totalPayments > 0 && totalRevenue != null) {
                averagePayment = totalRevenue / totalPayments;
            }

            revenueData.put("totalRevenue", totalRevenue != null ? totalRevenue : 0.0);
            revenueData.put("totalPayments", totalPayments);
            revenueData.put("averagePayment", Math.round(averagePayment * 100.0) / 100.0);
            revenueData.put("dailyRevenue", dailyRevenue);
            revenueData.put("dateRange", Map.of(
                    "from", startDate != null ? startDate.toLocalDate().toString() : "all",
                    "to", endDate != null ? endDate.toLocalDate().toString() : "all"));

            log.info("Payment-based revenue calculated successfully. Total: {}, Payments: {}",
                    totalRevenue, totalPayments);
            return revenueData;

        } catch (Exception e) {
            log.error("Error calculating payment-based revenue: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to calculate payment-based revenue", e);
        }
    }

    /**
     * Helper method to parse date strings for filtering
     */
    private LocalDateTime parseDate(String dateStr, boolean isStartDate) {
        if (!StringUtils.hasText(dateStr)) {
            return null;
        }

        try {
            LocalDate date = LocalDate.parse(dateStr);
            return isStartDate ? date.atStartOfDay() : date.atTime(23, 59, 59);
        } catch (Exception e) {
            log.warn("Invalid date format: {}. Using null instead.", dateStr);
            return null;
        }
    }
}
