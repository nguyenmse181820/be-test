package com.boeing.bookingservice.service.impl;

import com.boeing.bookingservice.dto.request.CreateBookingRequestDTO;
import com.boeing.bookingservice.dto.request.CreatePaymentRequest;
import com.boeing.bookingservice.dto.request.PassengerInfoDTO;
import com.boeing.bookingservice.dto.request.SeatSelectionDTO;
import com.boeing.bookingservice.dto.response.ApiResponse;
import com.boeing.bookingservice.dto.response.BookingFullDetailResponseDTO;
import com.boeing.bookingservice.dto.response.BookingInitiatedResponseDTO;
import com.boeing.bookingservice.dto.response.BookingSummaryDTO;
import com.boeing.bookingservice.exception.BadRequestException;
import com.boeing.bookingservice.exception.ResourceNotFoundException;
import com.boeing.bookingservice.exception.SagaProcessingException;
import com.boeing.bookingservice.integration.fs.FlightClient;
import com.boeing.bookingservice.integration.fs.dto.FsReleaseFareRequestDTO;
import com.boeing.bookingservice.integration.ls.LoyaltyClient;
import com.boeing.bookingservice.integration.ls.dto.LsUserVoucherDTO;
import com.boeing.bookingservice.mapper.BookingMapper;
import com.boeing.bookingservice.mapper.PassengerMapper;
import com.boeing.bookingservice.model.entity.Booking;
import com.boeing.bookingservice.model.entity.BookingDetail;
import com.boeing.bookingservice.model.entity.Passenger;
import com.boeing.bookingservice.model.entity.Payment;
import com.boeing.bookingservice.model.enums.*;
import com.boeing.bookingservice.repository.BookingRepository;
import com.boeing.bookingservice.repository.PassengerRepository;
import com.boeing.bookingservice.repository.PaymentRepository;
import com.boeing.bookingservice.event.BookingCompletedEvent;
import com.boeing.bookingservice.saga.orchestrator.CreateBookingSagaOrchestrator;
import com.boeing.bookingservice.service.BookingService;
import com.boeing.bookingservice.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.ApplicationEventPublisher;

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

    private final Map<UUID, CompletableFuture<BookingInitiatedResponseDTO>> pendingSagaResponses = new ConcurrentHashMap<>();    @Override
    public BookingInitiatedResponseDTO initiateBookingCreationSaga(CreateBookingRequestDTO createBookingRequest, UUID userId, String clientIpAddress) {
        log.info("Initiating booking creation saga for user: {} with flightId: {} from IP: {}", userId, createBookingRequest.getFlightId(), clientIpAddress);
        if (createBookingRequest.getSeatSelections() != null && !createBookingRequest.getSeatSelections().isEmpty()) {
            log.info("[SEAT_DEBUG] Request có {} seat selections:", createBookingRequest.getSeatSelections().size());
            for (int i = 0; i < createBookingRequest.getSeatSelections().size(); i++) {
                var seat = createBookingRequest.getSeatSelections().get(i);
                log.info("[SEAT_DEBUG] Seat[{}]: code={}, passengerIndex={}", 
                        i, seat.getSeatCode(), seat.getPassengerIndex());
            }
        } else {
            log.info("[SEAT_DEBUG] Request KHÔNG có seat selections!");
        }
        
        // Validation
        if (createBookingRequest.getFlightId() == null || createBookingRequest.getPassengers() == null || createBookingRequest.getPassengers().isEmpty()) {
            throw new BadRequestException("Flight ID and passenger information are required.");
        }
        
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
    }    @Transactional
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
                .build();        List<BookingDetail> bookingDetailsList = new ArrayList<>();
        int passengerCount = passengerInfosFromRequest.size();
        if (passengerCount == 0) {
            throw new BadRequestException("Passenger list cannot be empty for booking.");
        }        // Validate and create seat selection map using simple index lookup
        Map<Integer, SeatSelectionDTO> seatSelectionByIndex = new HashMap<>();
        if (seatSelections != null && !seatSelections.isEmpty()) {
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
                    if (seatSelectionByIndex.containsKey(passengerIndex)) {
                        throw new BadRequestException(
                            String.format("Duplicate seat selection for passenger index %d", passengerIndex)
                        );
                    }
                    
                    seatSelectionByIndex.put(passengerIndex, seatSelection);
                    log.info("[SEAT_DEBUG] ✅ Mapped passenger[{}] -> seat {}", 
                            passengerIndex, seatSelection.getSeatCode());
                }
            }
            
            log.info("[SEAT_DEBUG] Successfully mapped {} seats", seatSelectionByIndex.size());
        } else {
            log.info("[SEAT_DEBUG] No seat selections to process");
        }

        for (int passengerIndex = 0; passengerIndex < passengerInfosFromRequest.size(); passengerIndex++) {
            PassengerInfoDTO passengerRequest = passengerInfosFromRequest.get(passengerIndex);
            Passenger passengerEntity;
            if (passengerRequest.getId() != null) {
                passengerEntity = passengerRepository.findByIdAndUserId(passengerRequest.getId(), userId)
                        .orElseGet(() -> {
                            log.warn("Passenger ID {} (provided by user {}) not found or not owned. Creating new passenger.", passengerRequest.getId(), userId);
                            Passenger newP = passengerMapper.toPassengerEntity(passengerRequest);
                            newP.setId(null);
                            newP.setUserId(userId);
                            return passengerRepository.save(newP);
                        });
            } else {
                passengerEntity = passengerMapper.toPassengerEntity(passengerRequest);
                passengerEntity.setUserId(userId);
                passengerEntity = passengerRepository.save(passengerEntity);
            }

            double detailPrice = totalAmount / passengerCount;
            detailPrice = Math.round(detailPrice * 100.0) / 100.0;            // Find seat selection for this passenger by index
            SeatSelectionDTO matchingSeatSelection = seatSelectionByIndex.get(passengerIndex);
            String selectedSeatCode = matchingSeatSelection != null ? matchingSeatSelection.getSeatCode() : null;
            
            log.debug("Passenger {} (index {}): selectedSeatCode = {}", 
                    passengerRequest.getFirstName(), passengerIndex, selectedSeatCode);

            BookingDetail detail = BookingDetail.builder()
                    .booking(booking)
                    .passenger(passengerEntity)
                    .flightId(flightId)
                    .flightCode(snapshotFlightCode)
                    .originAirportCode(snapshotOriginAirportCode)
                    .destinationAirportCode(snapshotDestinationAirportCode)
                    .departureTime(snapshotDepartureTime)
                    .arrivalTime(snapshotArrivalTime)
                    .selectedFareName(selectedFareNameFromRequest)
                    .selectedSeatCode(selectedSeatCode)
                    .price(detailPrice)
                    .status(BookingDetailStatus.PENDING_PAYMENT)
                    .bookingCode(bookingReferenceForDisplay)
                    .build();
            bookingDetailsList.add(detail);
        }
        booking.setBookingDetails(bookingDetailsList);

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

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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

    @Override
    @Transactional
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

    @Override
    @Transactional(readOnly = true)
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

    @Override
    @Transactional(readOnly = true)
    public Page<BookingSummaryDTO> getUserBookings(UUID userId, Pageable pageable) {
        log.info("Service: Fetching bookings for user: {} with pageable: {}", userId, pageable);
        if (userId == null) {
            throw new BadRequestException("User ID cannot be null for fetching user bookings.");
        }

        Page<Booking> bookingPage = bookingRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);

        return bookingPage.map(bookingMapper::toBookingSummaryDTO);
    }

    @Override
    @Transactional(readOnly = true)
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

    @Override
    @Transactional
    public void handlePostPaymentWorkflow(String bookingReference) {
        try {
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
            
            eventPublisher.publishEvent(completedEvent);        } catch (Exception e) {
            log.error("[POST_PAYMENT_WORKFLOW] Error in post-payment workflow for booking: {}",
                    bookingReference, e);// This should be handled by a retry mechanism or manual intervention
        }
    }
}
