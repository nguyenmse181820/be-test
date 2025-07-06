package com.boeing.bookingservice.service.impl;

import com.boeing.bookingservice.dto.request.CreateBookingRequestDTO;
import com.boeing.bookingservice.dto.request.PassengerInfoDTO;
import com.boeing.bookingservice.dto.request.SeatSelectionDTO;
import com.boeing.bookingservice.dto.response.BookingFullDetailResponseDTO;
import com.boeing.bookingservice.dto.response.BookingInitiatedResponseDTO;
import com.boeing.bookingservice.dto.response.BookingSummaryDTO;
import com.boeing.bookingservice.exception.BadRequestException;
import com.boeing.bookingservice.exception.ResourceNotFoundException;
import com.boeing.bookingservice.exception.SagaProcessingException;
import com.boeing.bookingservice.integration.fs.FlightClient;
import com.boeing.bookingservice.integration.fs.dto.FsReleaseFareRequestDTO;
import com.boeing.bookingservice.integration.ls.LoyaltyClient;
import com.boeing.bookingservice.dto.response.ApiResponse;
import com.boeing.bookingservice.integration.ls.dto.LsUserVoucherDTO;
import com.boeing.bookingservice.mapper.BookingMapper;
import com.boeing.bookingservice.mapper.PassengerMapper;
import com.boeing.bookingservice.model.entity.Booking;
import com.boeing.bookingservice.model.entity.BookingDetail;
import com.boeing.bookingservice.model.entity.Passenger;
import com.boeing.bookingservice.model.entity.Payment;
import com.boeing.bookingservice.model.enums.BookingDetailStatus;
import com.boeing.bookingservice.model.enums.BookingStatus;
import com.boeing.bookingservice.model.enums.BookingType;
import com.boeing.bookingservice.model.enums.PaymentStatus;
import com.boeing.bookingservice.model.enums.PaymentType;
import com.boeing.bookingservice.event.BookingCompletedEvent;
import com.boeing.bookingservice.dto.request.CreatePaymentRequest;
import com.boeing.bookingservice.repository.BookingRepository;
import com.boeing.bookingservice.repository.PassengerRepository;
import com.boeing.bookingservice.repository.PaymentRepository;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


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
    private final LoyaltyClient loyaltyClient;
    @Lazy
    private final PaymentService paymentService;
    private final ApplicationEventPublisher eventPublisher;

    private final Map<UUID, CompletableFuture<BookingInitiatedResponseDTO>> pendingSagaResponses = new ConcurrentHashMap<>();
    
    /**
     * Initiates a booking creation saga. This method validates the request and starts
     * the saga process for both single-segment and multi-segment bookings.
     * Transaction is not started here since this method only validates and delegates to the saga.
     */
    @Override
    public BookingInitiatedResponseDTO initiateBookingCreationSaga(CreateBookingRequestDTO createBookingRequest, UUID userId, String clientIpAddress) {
        // Handle new multi-segment format or legacy single-segment format
        boolean isMultiSegment = createBookingRequest.getFlightIds() != null && !createBookingRequest.getFlightIds().isEmpty();
        
        if (isMultiSegment) {
            log.info("Initiating multi-segment booking creation saga for user: {} with {} flight segments from IP: {}", 
                    userId, createBookingRequest.getFlightIds().size(), clientIpAddress);
            
            // Log seat selections for multi-segment booking
            if (createBookingRequest.getSelectedSeatsByFlight() != null && !createBookingRequest.getSelectedSeatsByFlight().isEmpty()) {
                log.info("[SEAT_DEBUG] Multi-segment request has seat selections for {} flights:", 
                        createBookingRequest.getSelectedSeatsByFlight().size());
                createBookingRequest.getSelectedSeatsByFlight().forEach((flightIndex, seats) -> {
                    log.info("[SEAT_DEBUG] Flight[{}]: {} seats: {}", flightIndex, seats.size(), seats);
                });
            } else {
                log.info("[SEAT_DEBUG] Multi-segment request DOESN'T have seat selections!");
            }
        } else {
            log.info("Initiating single-segment booking creation saga for user: {} with flightId: {} from IP: {}", 
                    userId, createBookingRequest.getFlightId(), clientIpAddress);
            
            // Log seat selections for single-segment booking
            if (createBookingRequest.getSeatSelections() != null && !createBookingRequest.getSeatSelections().isEmpty()) {
                log.info("[SEAT_DEBUG] Single-segment request has {} seat selections:", 
                        createBookingRequest.getSeatSelections().size());
                for (int i = 0; i < createBookingRequest.getSeatSelections().size(); i++) {
                    var seat = createBookingRequest.getSeatSelections().get(i);
                    log.info("[SEAT_DEBUG] Seat[{}]: code={}, passengerIndex={}", 
                            i, seat.getSeatCode(), seat.getPassengerIndex());
                }
            } else {
                log.info("[SEAT_DEBUG] Single-segment request DOESN'T have seat selections!");
            }
        }
        
        // Validation based on booking type
        if (isMultiSegment) {
            // Multi-segment validation
            if (createBookingRequest.getFlightIds().isEmpty() || createBookingRequest.getPassengers() == null || createBookingRequest.getPassengers().isEmpty()) {
                throw new BadRequestException("Flight IDs list and passenger information are required.");
            }
        } else {
            // Single-segment validation
            if (createBookingRequest.getFlightId() == null || createBookingRequest.getPassengers() == null || createBookingRequest.getPassengers().isEmpty()) {
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
        }        UUID bookingId = UUID.randomUUID();

        UUID sagaId = bookingId; // Use booking ID as saga ID for consistency
        String bookingReferenceForUser = "BKG-" + sagaId.toString().substring(0, 8).toUpperCase();

        CompletableFuture<BookingInitiatedResponseDTO> sagaResultFuture = new CompletableFuture<>();
        pendingSagaResponses.put(sagaId, sagaResultFuture);

        createBookingSagaOrchestrator.startSaga(createBookingRequest, userId, sagaId, bookingReferenceForUser, clientIpAddress);
        log.info("[BOOKING_INIT][SagaID:{}] Saga started with UserBookingRef: {}", sagaId, bookingReferenceForUser);

        try {
            return sagaResultFuture.get(30, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.error("[BOOKING_INIT_TIMEOUT][SagaID:{}] Timeout waiting for saga result. UserBookingRef: {}", sagaId, bookingReferenceForUser, e);
            pendingSagaResponses.remove(sagaId);
            createBookingSagaOrchestrator.cancelSaga(sagaId, "CLIENT_API_TIMEOUT");
            throw new SagaProcessingException("Booking processing timed out. Please try again. Ref: " + bookingReferenceForUser, e);
        } catch (InterruptedException e) {
            log.error("[BOOKING_INIT_INTERRUPTED][SagaID:{}] Saga result future interrupted. UserBookingRef: {}", sagaId, bookingReferenceForUser, e);
            pendingSagaResponses.remove(sagaId);
            Thread.currentThread().interrupt();
            throw new SagaProcessingException("Booking processing was interrupted. Ref: " + bookingReferenceForUser, e);
        } catch (ExecutionException e) {
            log.error("[BOOKING_INIT_EXECUTION_ERROR][SagaID:{}] Error executing saga. UserBookingRef: {}", sagaId, bookingReferenceForUser, e.getCause());
            pendingSagaResponses.remove(sagaId);
            if (e.getCause() instanceof SagaProcessingException) {
                throw (SagaProcessingException) e.getCause();
            }
            throw new SagaProcessingException("Failed to process booking. Ref: " + bookingReferenceForUser, e);
        }
    }

    /**
     * Creates a pending booking in the database for a single flight segment.
     * Uses REQUIRES_NEW to ensure a new transaction is always started,
     * with REPEATABLE_READ isolation to prevent dirty reads during booking creation.
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
            String clientIpAddress    ) {
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
                bookingReferenceForDisplay
        );
        
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
        log.info("Service: Pending booking entities (Booking, BookingDetail, Passenger) created successfully. DB ID: {}, Ref: {}", savedBooking.getId(), savedBooking.getBookingReference());

        try {
            CreatePaymentRequest paymentReq = new CreatePaymentRequest();
            paymentReq.setBookingReference(savedBooking.getBookingReference());
            paymentReq.setAmount(savedBooking.getTotalAmount());
            paymentReq.setOrderDescription("Thanh toan don hang " + savedBooking.getBookingReference());

            String vnpayPaymentUrl = paymentService.createVNPayPaymentUrl(paymentReq, userId);

            if (!StringUtils.hasText(vnpayPaymentUrl)) {
                log.error("[VNPAY_URL_ERROR][ID:{}, Ref:{}] PaymentService returned empty VNPAY payment URL.",
                        savedBooking.getId(), savedBooking.getBookingReference());
                failSagaInitiationBeforePaymentUrl(savedBooking.getId(), savedBooking.getBookingReference(), "Failed to generate VNPAY payment URL (empty URL returned).");
                throw new SagaProcessingException("Failed to generate VNPAY payment URL for " + savedBooking.getBookingReference());
            }

            completeSagaInitiationWithPaymentUrl(
                    savedBooking.getId(),
                    savedBooking.getBookingReference(),
                    savedBooking.getTotalAmount(),
                    vnpayPaymentUrl,
                    savedBooking.getPaymentDeadline()
            );
        } catch (Exception e) {
            log.error("[VNPAY_URL_ERROR][ID:{}, Ref:{}] Exception while getting VNPAY payment URL: {}",
                    savedBooking.getId(), savedBooking.getBookingReference(), e.getMessage(), e);
            failSagaInitiationBeforePaymentUrl(savedBooking.getId(), savedBooking.getBookingReference(), "Failed to generate VNPAY payment URL: " + e.getMessage());
            throw new SagaProcessingException("Failed to generate VNPAY payment URL for booking " + savedBooking.getBookingReference(), e);
        }
        return savedBooking;
    }

    /**
     * Creates a pending booking in the database for multiple flight segments (connecting flights).
     * Uses REQUIRES_NEW to ensure a new transaction is always started,
     * with REPEATABLE_READ isolation to prevent dirty reads during booking creation.
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
            String clientIpAddress) {
        
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
        
        // Create booking entity
        Booking booking = Booking.builder()
                .id(bookingId)
                .bookingReference(bookingReferenceForDisplay)
                .userId(userId)
                .bookingDate(LocalDate.now())
                .totalAmount(totalAmount)
                .status(BookingStatus.PENDING_PAYMENT)
                .type(BookingType.MULTI_SEGMENT)
                .paymentDeadline(paymentDeadline)
                .appliedVoucherCode(appliedVoucherCode)
                .build();
        
        List<BookingDetail> allBookingDetails = new ArrayList<>();
        int passengerCount = passengerInfosFromRequest.size();
        
        if (passengerCount == 0) {
            throw new BadRequestException("Passenger list cannot be empty for booking.");
        }
        
        // Calculate base fare per passenger per segment
        double baseFarePerPassengerPerSegment = totalAmount / (passengerCount * flightIds.size());
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
            
            // Handle LocalDateTime conversion - may come as ArrayList from JSON deserialization
            LocalDateTime departureTime = convertToLocalDateTime(departureTimeObj);
            LocalDateTime arrivalTime = convertToLocalDateTime(arrivalTimeObj);
            
            // Get seat selections for this flight and convert to passenger index -> seat code map
            List<String> flightSeats = selectedSeatsByFlight != null ? 
                    selectedSeatsByFlight.getOrDefault(String.valueOf(flightIndex), Collections.emptyList()) : 
                    Collections.emptyList();
            
            Map<Integer, String> seatMap = new HashMap<>();
            for (int i = 0; i < flightSeats.size() && i < passengerCount; i++) {
                seatMap.put(i, flightSeats.get(i));
            }
            
            log.info("[MULTI_SEGMENT] Processing flight index {}, ID: {}, seats: {}", 
                    flightIndex, flightId, flightSeats.size());
            
            // Use utility method to create booking details for all passengers on this segment
            String segmentBookingCode = bookingReferenceForDisplay + "-" + (flightIndex + 1);
            
            BookingUtils.BookingDetailsResult segmentResult = BookingUtils.createBookingDetailsForSegment(
                    booking,
                    passengerEntities,
                    passengerInfosFromRequest,
                    flightId,
                    flightCode,
                    originAirportCode,
                    destinationAirportCode,
                    departureTime,
                    arrivalTime,
                    selectedFareNameFromRequest,
                    seatMap,
                    baseFarePerPassengerPerSegment,
                    segmentBookingCode
            );
            
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
    public void completeSagaInitiationWithPaymentUrl(UUID sagaId, String bookingReferenceDisplay, Double totalAmount, String vnpayPaymentUrl, LocalDateTime paymentDeadline) {
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
            log.info("[SAGA_INIT_COMPLETE][SagaID:{}] Saga initiation completed, VNPAY URL provided for UserBookingRef: {}", sagaId, bookingReferenceDisplay);
        } else {
            log.warn("[SAGA_INIT_WARN][SagaID:{}] No pending future found to complete for UserBookingRef: {}", sagaId, bookingReferenceDisplay);
        }
    }

    @Override
    public void failSagaInitiationBeforePaymentUrl(UUID sagaId, String bookingReferenceDisplay, String errorMessage) {
        CompletableFuture<BookingInitiatedResponseDTO> sagaResultFuture = pendingSagaResponses.remove(sagaId);
        if (sagaResultFuture != null) {
            sagaResultFuture.completeExceptionally(new SagaProcessingException("Saga failed for booking " + bookingReferenceDisplay + ": " + errorMessage));
            log.error("[SAGA_INIT_FAIL][SagaID:{}] Saga initiation failed for UserBookingRef {}: {}", sagaId, bookingReferenceDisplay, errorMessage);
        }
    }

    /**
     * Cancels an overdue pending booking.
     * Uses REQUIRES_NEW to ensure a new transaction is always started,
     * even if called from another transactional method.
     * Uses READ_COMMITTED isolation to allow reading data committed by other transactions.
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

                log.info("Service Scheduler: Cancelling booking ID {}, Ref {}", booking.getId(), booking.getBookingReference());
                booking.setStatus(BookingStatus.CANCELLED_NO_PAYMENT);

                Optional<Payment> bookingPaymentOpt = paymentRepository.findByBooking(booking);
                bookingPaymentOpt.ifPresent(payment -> {
                    if (payment.getPaymentType() == PaymentType.BOOKING_INITIAL && payment.getStatus() == PaymentStatus.PENDING) {
                        payment.setStatus(PaymentStatus.EXPIRED);
                        paymentRepository.save(payment);
                        log.info("Service Scheduler: Payment ID {} for booking {} status set to EXPIRED.", payment.getId(), booking.getBookingReference());
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
                            flightClient.releaseFare(detail.getFlightId(), releaseRequest.getFareName(), releaseRequest);
                            log.info("Service Scheduler: Fare inventory release requested for booking detail {}, flightId {}, fare {}",
                                    detail.getId(), detail.getFlightId(), detail.getSelectedFareName());
                        } catch (Exception e) {
                            log.error("Service Scheduler: Failed to request fare inventory release for detail {}: {}", detail.getId(), e.getMessage());
                        }
                    }
                }
                bookingRepository.save(booking);
                log.info("Service Scheduler: Booking {} (ID: {}) cancelled due to payment timeout.", booking.getBookingReference(), booking.getId());

                // TODO: Gửi thông báo cho user
            } else {
                log.info("Service Scheduler: Booking {} (ID: {}) not cancelled. Status: {} or Payment Deadline {} not passed yet (Current time: {}).",
                        booking.getBookingReference(), booking.getId(), booking.getStatus(), booking.getPaymentDeadline(), LocalDateTime.now());
            }
        } else {
            log.warn("Service Scheduler: Booking with identifier {} not found for cancellation.", bookingIdentifier);
        }
    }

    /**
     * Updates the booking status after payment processing.
     * Uses REQUIRED propagation as this should be called within an existing transaction.
     * Uses REPEATABLE_READ isolation to ensure consistent reads during the update.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.REPEATABLE_READ)
    public void updateBookingStatusAndDetailsPostPayment(String bookingReference, boolean paymentSuccessfulAndSeatsConfirmed, String vnpayTransactionIdIfSuccessful) {
        log.info("Service: Updating booking status post-payment for bookingRef: {}. Overall success: {}, VNPAY TxnNo: {}",
                bookingReference, paymentSuccessfulAndSeatsConfirmed, vnpayTransactionIdIfSuccessful);

        Booking booking = bookingRepository.findByBookingReference(bookingReference)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", "reference", bookingReference));

        if (paymentSuccessfulAndSeatsConfirmed) {
            if (booking.getStatus() == BookingStatus.PENDING_PAYMENT) {
                booking.setStatus(BookingStatus.PAID);
                booking.getBookingDetails().forEach(detail -> detail.setStatus(BookingDetailStatus.BOOKED));
                log.info("Service: Booking {} (ID: {}) status updated to PAID.", booking.getBookingReference(), booking.getId());

            } else {
                log.warn("Service: Booking {} (ID: {}) not in PENDING_PAYMENT state (current: {}). Update to PAID skipped by post-payment handler.",
                        booking.getBookingReference(), booking.getId(), booking.getStatus());
            }
        } else {
            if (booking.getStatus() == BookingStatus.PENDING_PAYMENT) {
                Payment primaryPayment = paymentRepository.findByBookingAndPaymentType(booking, PaymentType.BOOKING_INITIAL)
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
                log.warn("Service: Booking {} (ID: {}) not in PENDING_PAYMENT state (current: {}). Update due to failure skipped.",
                        booking.getBookingReference(), booking.getId(), booking.getStatus());
            }
        }
        bookingRepository.save(booking);
    }

    /**
     * Retrieves detailed booking information by booking reference.
     * Uses readOnly=true for optimized read performance.
     * Uses READ_COMMITTED isolation for fast reads while ensuring data is committed.
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public BookingFullDetailResponseDTO getBookingDetailsByReference(String bookingReference, UUID currentUserId, String currentUserRole) {
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
     * Uses READ_COMMITTED isolation for fast reads while ensuring data is committed.
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public Page<BookingSummaryDTO> getUserBookings(UUID userId, Pageable pageable) {
        log.info("Service: Fetching bookings for user: {} with pageable: {}", userId, pageable);
        if (userId == null) {
            throw new BadRequestException("User ID cannot be null for fetching user bookings.");
        }

        Page<Booking> bookingPage = bookingRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);

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
                    transactionId
            );
            
            log.info("[POST_PAYMENT_WORKFLOW] Publishing booking completed event for booking: {}, userId: {}",
                     bookingReference, booking.getUserId());
            eventPublisher.publishEvent(completedEvent);
            log.info("[POST_PAYMENT_WORKFLOW] Post-payment workflow completed successfully for booking: {}", bookingReference);
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
     * Enhanced pricing calculation that considers age categories and seat selections
     * Delegates to BookingUtils for core calculation while adding logging
     */
    private double calculatePassengerPrice(PassengerInfoDTO passenger, SeatSelectionDTO seatSelection, double baseFarePrice) {
        String ageCategory = getAgeCategory(passenger.getDateOfBirth());
        String selectedFareName = (seatSelection != null) ? seatSelection.getSelectedFareName() : null;
        
        // Calculate price using utility method
        double finalPrice = BookingUtils.calculatePassengerPrice(ageCategory, baseFarePrice, selectedFareName);
        
        // Add detailed logging
        if ("baby".equals(ageCategory)) {
            log.debug("Passenger {} is a baby, using fixed price: {}", passenger.getFirstName(), BookingUtils.BABY_PRICE);
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
    
    // Removed calculatePassengerSegmentPrice method - now using BookingUtils.calculatePassengerPrice directly

    /**
     * Common method to process passenger data for both single and multi-segment bookings
     * Helps reduce code duplication between booking flows
     * 
     * @param passengers List of passenger information from request
     * @param userId User ID making the booking
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
     * @param seatSelections List of seat selections
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
                            passengerIndex, seatSelection.getSeatCode(), passengerInfosFromRequest.size() - 1)
                    );
                }
                
                // Check for duplicate passenger index
                if (seatSelectionMap.containsKey(passengerIndex)) {
                    throw new BadRequestException(
                        String.format("Duplicate seat selection for passenger index %d", passengerIndex)
                    );
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
     * Helper method to convert Object (potentially ArrayList from JSON deserialization)
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
            // JSON deserialization may convert LocalDateTime to [year, month, day, hour, minute, second, nano]
            List<?> dateList = (List<?>) dateTimeObj;
            log.debug("Converting ArrayList to LocalDateTime: size={}, content={}", dateList.size(), dateList);
            
            if (dateList.size() >= 6) {
                try {
                    int year = ((Number) dateList.get(0)).intValue();
                    int month = ((Number) dateList.get(1)).intValue();
                    int day = ((Number) dateList.get(2)).intValue();
                    int hour = ((Number) dateList.get(3)).intValue();
                    int minute = ((Number) dateList.get(4)).intValue();
                    int second = ((Number) dateList.get(5)).intValue();
                    int nano = dateList.size() > 6 ? ((Number) dateList.get(6)).intValue() : 0;
                    
                    LocalDateTime result = LocalDateTime.of(year, month, day, hour, minute, second, nano);
                    log.debug("Successfully converted ArrayList {} to LocalDateTime: {}", dateList, result);
                    return result;
                } catch (Exception e) {
                    log.error("Failed to convert ArrayList to LocalDateTime: {}", dateList, e);
                    throw new IllegalArgumentException("Invalid date format in flight details", e);
                }
            } else {
                log.error("ArrayList has insufficient elements for LocalDateTime conversion: size={}, content={}", dateList.size(), dateList);
                throw new IllegalArgumentException("Invalid date format: ArrayList has " + dateList.size() + " elements, need at least 6");
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
}
