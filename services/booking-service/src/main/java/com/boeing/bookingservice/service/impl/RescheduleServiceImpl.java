package com.boeing.bookingservice.service.impl;

import com.boeing.bookingservice.dto.request.CreatePaymentRequest;
import com.boeing.bookingservice.dto.request.RescheduleFlightRequestDTO;
import com.boeing.bookingservice.dto.response.RescheduleFlightResponseDTO;
import com.boeing.bookingservice.exception.BadRequestException;
import com.boeing.bookingservice.exception.ResourceNotFoundException;
import com.boeing.bookingservice.integration.fs.FlightClient;
import com.boeing.bookingservice.integration.fs.dto.*;
import com.boeing.bookingservice.model.entity.BookingDetail;
import com.boeing.bookingservice.model.entity.Passenger;
import com.boeing.bookingservice.model.entity.RescheduleFlightHistory;
import com.boeing.bookingservice.model.enums.BookingDetailStatus;
import com.boeing.bookingservice.repository.BookingDetailRepository;
import com.boeing.bookingservice.repository.ReScheduleFlightRepository;
import com.boeing.bookingservice.saga.SagaStep;
import com.boeing.bookingservice.saga.orchestrator.RescheduleFlightSagaOrchestrator;
import com.boeing.bookingservice.saga.state.SagaState;
import com.boeing.bookingservice.saga.state.SagaStateRepository;
import com.boeing.bookingservice.service.PaymentService;
import com.boeing.bookingservice.service.RescheduleService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.Collections;

@Service
@RequiredArgsConstructor
@Slf4j
public class RescheduleServiceImpl implements RescheduleService {

    private static final long MIN_HOURS_BEFORE_DEPARTURE = 24;
    private final BookingDetailRepository bookingDetailRepository;
    private final ReScheduleFlightRepository rescheduleFlightRepository;
    private final FlightClient flightClient;
    private final PaymentService paymentService;
    private final SagaStateRepository sagaStateRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.READ_COMMITTED)
    public RescheduleFlightResponseDTO rescheduleBookingDetail(UUID bookingDetailId,
            RescheduleFlightRequestDTO rescheduleRequest, UUID userId, String clientIpAddress) {
        log.info("Processing reschedule request for booking detail: {}, user: {}, clientIP: {}",
                bookingDetailId, userId, clientIpAddress);

        // 1. Validate và lấy booking detail
        BookingDetail bookingDetail = validateAndGetBookingDetail(bookingDetailId, userId);

        // 2. Kiểm tra điều kiện đổi vé
        validateRescheduleConditions(bookingDetail);

        // 3. Lấy thông tin chuyến bay mới
        FsFlightWithFareDetailsDTO newFlightDetails = getNewFlightDetails(rescheduleRequest.getNewFlightId());

        // 4. Validate fare mới
        FsDetailedFareDTO newFare = validateNewFare(newFlightDetails, rescheduleRequest.getNewFareName());

        // 5. Kiểm tra và confirm ghế mới
        String confirmedSeatCode = handleSeatReservation(rescheduleRequest, newFlightDetails);

        // 6. Release ghế cũ
        releasePreviousSeat(bookingDetail);

        // 7. Tính toán giá và xử lý payment nếu cần
        RescheduleFlightResponseDTO response = calculatePriceAndHandlePayment(
                bookingDetail, newFlightDetails, newFare, confirmedSeatCode, rescheduleRequest, clientIpAddress);

        // 8. Cập nhật booking detail và tạo history
        updateBookingDetailAndCreateHistory(bookingDetail, rescheduleRequest, response, confirmedSeatCode);

        log.info("Reschedule completed successfully for booking detail: {}", bookingDetailId);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canReschedule(UUID bookingDetailId, UUID userId) {
        try {
            BookingDetail bookingDetail = validateAndGetBookingDetail(bookingDetailId, userId);
            validateRescheduleConditions(bookingDetail);
            return true;
        } catch (Exception e) {
            log.debug("Cannot reschedule booking detail {}: {}", bookingDetailId, e.getMessage());
            return false;
        }
    }

    private BookingDetail validateAndGetBookingDetail(UUID bookingDetailId, UUID userId) {
        BookingDetail bookingDetail = bookingDetailRepository.findById(bookingDetailId)
                .orElseThrow(() -> new ResourceNotFoundException("BookingDetail", "id", bookingDetailId));

        // Kiểm tra quyền sở hữu
        if (!bookingDetail.getBooking().getUserId().equals(userId)) {
            throw new AccessDeniedException("You don't have permission to reschedule this booking");
        }

        return bookingDetail;
    }

    private void validateRescheduleConditions(BookingDetail bookingDetail) {
        // Không cho phép đổi vé nếu đã RESCHEDULED
        if (bookingDetail.getStatus() == BookingDetailStatus.RESCHEDULED) {
            throw new BadRequestException("This booking detail has already been rescheduled and cannot be rescheduled again.");
        }
        // Kiểm tra status - chỉ cho phép đổi vé đã PAID
        if (bookingDetail.getStatus() != BookingDetailStatus.BOOKED) {
            throw new BadRequestException("Only confirmed bookings can be rescheduled. Current status: " +
                    bookingDetail.getStatus());
        }

        // Kiểm tra thời gian - phải ít nhất 24 giờ trước giờ khởi hành
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime departureTime = bookingDetail.getDepartureTime();

        long hoursUntilDeparture = ChronoUnit.HOURS.between(now, departureTime);
        if (hoursUntilDeparture < MIN_HOURS_BEFORE_DEPARTURE) {
            throw new BadRequestException(String.format(
                    "Cannot reschedule within 24 hours of departure. Current time until departure: %d hours",
                    hoursUntilDeparture));
        }

        log.info("Reschedule validation passed. Hours until departure: {}", hoursUntilDeparture);
    }

    private FsFlightWithFareDetailsDTO getNewFlightDetails(UUID newFlightId) {
        try {
            return flightClient.getFlightDetails(newFlightId);
        } catch (Exception e) {
            log.error("Failed to get flight details for flight: {}", newFlightId, e);
            throw new BadRequestException("New flight not found or not available");
        }
    }

    private FsDetailedFareDTO validateNewFare(FsFlightWithFareDetailsDTO flightDetails, String fareName) {
        return flightDetails.getAvailableFares().stream()
                .filter(fare -> fare.getName().equalsIgnoreCase(fareName))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Fare '" + fareName + "' not available for the new flight"));
    }

    private String handleSeatReservation(RescheduleFlightRequestDTO rescheduleRequest,
            FsFlightWithFareDetailsDTO newFlightDetails) {
        String requestedSeatCode = rescheduleRequest.getNewSeatCode();

        if (requestedSeatCode != null) {
            // Kiểm tra ghế có available không
            FsSeatsAvailabilityResponseDTO seatAvailability = flightClient.checkSeatsAvailability(
                    rescheduleRequest.getNewFlightId(), Collections.singletonList(requestedSeatCode));

            if (!seatAvailability.isAllRequestedSeatsAvailable()) {
                throw new BadRequestException("Requested seat " + requestedSeatCode + " is not available");
            }

            return requestedSeatCode;
        } else {
            // Tự động chọn ghế available từ available fares
            return newFlightDetails.getAvailableFares().stream()
                    .filter(fare -> fare.getSeats() != null && !fare.getSeats().isEmpty())
                    .flatMap(fare -> fare.getSeats().stream())
                    .filter(seatCode -> {
                        // Kiểm tra từng ghế có available không
                        try {
                            FsSeatsAvailabilityResponseDTO availability = flightClient.checkSeatsAvailability(
                                    rescheduleRequest.getNewFlightId(), Collections.singletonList(seatCode));
                            return availability.isAllRequestedSeatsAvailable();
                        } catch (Exception e) {
                            log.warn("Could not check availability for seat {}: {}", seatCode, e.getMessage());
                            return false;
                        }
                    })
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException("No available seats for the new flight"));
        }
    }

    private void releasePreviousSeat(BookingDetail bookingDetail) {
        if (bookingDetail.getSelectedSeatCode() != null) {
            try {
                FsReleaseSeatsRequestDTO releaseRequest = FsReleaseSeatsRequestDTO.builder()
                        .bookingReference(bookingDetail.getBookingCode())
                        .seatCodes(Collections.singletonList(bookingDetail.getSelectedSeatCode()))
                        .reason("RESCHEDULE")
                        .build();

                flightClient.releaseSeats(bookingDetail.getFlightId(), releaseRequest);
                log.info("Released seat {} for flight {}", bookingDetail.getSelectedSeatCode(),
                        bookingDetail.getFlightId());
            } catch (Exception e) {
                log.error("Failed to release seat {} for flight {}: {}",
                        bookingDetail.getSelectedSeatCode(), bookingDetail.getFlightId(), e.getMessage());
            }
        }
    }

    private RescheduleFlightResponseDTO calculatePriceAndHandlePayment(
            BookingDetail bookingDetail,
            FsFlightWithFareDetailsDTO newFlightDetails,
            FsDetailedFareDTO newFare,
            String confirmedSeatCode,
            RescheduleFlightRequestDTO rescheduleRequest,
            String clientIpAddress) {

        // Giá cũ đã bao gồm VAT (đã được thanh toán)
        double oldPrice = bookingDetail.getPrice();

        // Giá mới cần cộng thêm 10% VAT để so sánh công bằng
        double newPriceBeforeVAT = newFare.getPrice();
        double newPriceWithVAT = newPriceBeforeVAT * 1.1;

        // Tính chênh lệch dựa trên giá đã bao gồm VAT
        double priceDifference = newPriceWithVAT - oldPrice;

        log.info(
                "Price calculation - Old price (with VAT): {}, New price before VAT: {}, New price with VAT: {}, Difference: {}",
                oldPrice, newPriceBeforeVAT, newPriceWithVAT, priceDifference);

        RescheduleFlightResponseDTO.RescheduleFlightResponseDTOBuilder responseBuilder = RescheduleFlightResponseDTO
                .builder()
                .bookingDetailId(bookingDetail.getId())
                .bookingReference(bookingDetail.getBooking().getBookingReference())
                .oldFlightId(bookingDetail.getFlightId())
                .oldFlightCode(bookingDetail.getFlightCode())
                .oldDepartureTime(bookingDetail.getDepartureTime())
                .oldSeatCode(bookingDetail.getSelectedSeatCode())
                .oldPrice(oldPrice)
                .newFlightId(newFlightDetails.getFlightId())
                .newFlightCode(newFlightDetails.getFlightCode())
                .newDepartureTime(newFlightDetails.getDepartureTime())
                .newSeatCode(confirmedSeatCode)
                .newPrice(newPriceWithVAT) // Hiển thị giá đã bao gồm VAT
                .priceDifference(priceDifference)
                .status("SUCCESS")
                .processedAt(LocalDateTime.now());

        if (priceDifference > 0) {
            // Vé mới đắt hơn - cần thanh toán thêm
            String paymentUrl = createAdditionalPayment(bookingDetail, priceDifference,
                    rescheduleRequest.getPaymentMethod(), clientIpAddress);
            responseBuilder
                    .paymentStatus("PAYMENT_REQUIRED")
                    .paymentUrl(paymentUrl)
                    .message("Reschedule successful. Additional payment required: "
                            + String.format("%.0f", priceDifference) + " VND");
        } else if (priceDifference < 0) {
            // Vé mới rẻ hơn - không refund theo chính sách
            responseBuilder
                    .paymentStatus("NO_REFUND")
                    .paymentUrl("")
                    .message("Reschedule successful. Price difference: "
                            + String.format("%.0f", Math.abs(priceDifference))
                            + " VND (no refund as per policy)");
        } else {
            // Giá bằng nhau
            responseBuilder
                    .paymentStatus("NO_PAYMENT_NEEDED")
                    .paymentUrl("")
                    .message("Reschedule successful. No price difference");
        }

        return responseBuilder.build();
    }

    private String createAdditionalPayment(BookingDetail bookingDetail, double amount, String paymentMethod,
            String clientIpAddress) {
        try {
            CreatePaymentRequest paymentRequest = new CreatePaymentRequest();
            // Create unique transaction reference for reschedule to avoid VNPay conflicts
            String uniqueTransactionRef = bookingDetail.getBooking().getBookingReference() + "_RESCHEDULE_"
                    + System.currentTimeMillis();
            paymentRequest.setBookingReference(uniqueTransactionRef);
            paymentRequest.setAmount(amount);
            paymentRequest.setOrderDescription(
                    "Additional payment for reschedule: " + bookingDetail.getBooking().getBookingReference());
            paymentRequest.setPaymentMethod(paymentMethod != null ? paymentMethod : "VNPAY");
            paymentRequest.setClientIpAddress(clientIpAddress != null ? clientIpAddress : "127.0.0.1");

            log.info(
                    "Creating additional payment for reschedule - OriginalBookingRef: {}, UniqueTransactionRef: {}, Amount: {}, PaymentMethod: {}, ClientIP: {}",
                    bookingDetail.getBooking().getBookingReference(), uniqueTransactionRef, amount,
                    paymentRequest.getPaymentMethod(),
                    paymentRequest.getClientIpAddress());

            // Sử dụng method có sẵn trong PaymentService
            return paymentService.createVNPayPaymentUrl(paymentRequest, bookingDetail.getBooking().getUserId());
        } catch (Exception e) {
            log.error("Failed to create payment URL for reschedule additional payment", e);
            throw new BadRequestException("Failed to create payment for additional amount");
        }
    }

    private void updateBookingDetailAndCreateHistory(
            BookingDetail bookingDetail,
            RescheduleFlightRequestDTO rescheduleRequest,
            RescheduleFlightResponseDTO response,
            String confirmedSeatCode) {

        // Store old values before updating
        UUID oldFlightId = bookingDetail.getFlightId();
        String oldSeatCode = bookingDetail.getSelectedSeatCode();
        double oldPrice = bookingDetail.getPrice();

        // Update booking detail
        bookingDetail.setFlightId(rescheduleRequest.getNewFlightId());
        bookingDetail.setFlightCode(response.getNewFlightCode());
        bookingDetail.setDepartureTime(response.getNewDepartureTime());
        bookingDetail.setSelectedFareName(rescheduleRequest.getNewFareName());
        bookingDetail.setSelectedSeatCode(confirmedSeatCode);
        bookingDetail.setPrice(response.getNewPrice());

        if (response.getPriceDifference() > 0) {
            // Cần thanh toán thêm - tạo saga để theo dõi
            UUID sagaId = UUID.randomUUID();

            // Tạo saga payload với thông tin đầy đủ
            RescheduleFlightSagaOrchestrator.RescheduleFlightSagaPayload sagaPayload = RescheduleFlightSagaOrchestrator.RescheduleFlightSagaPayload
                    .builder()
                    .bookingDetailId(bookingDetail.getId())
                    .bookingReference(bookingDetail.getBooking().getBookingReference())
                    .userId(bookingDetail.getBooking().getUserId())
                    // Old flight info
                    .oldFlightId(oldFlightId)
                    .oldFlightCode(bookingDetail.getFlightCode()) // Use old flight code from booking detail
                    .oldDepartureTime(bookingDetail.getDepartureTime()) // Use old departure time
                    .oldSeatCode(oldSeatCode)
                    .oldPrice(oldPrice)
                    // New flight info
                    .newFlightId(rescheduleRequest.getNewFlightId())
                    .newFlightCode(response.getNewFlightCode())
                    .newDepartureTime(response.getNewDepartureTime())
                    .newSeatCode(confirmedSeatCode)
                    .newFareName(rescheduleRequest.getNewFareName())
                    .newPrice(response.getNewPrice())
                    // Payment info
                    .priceDifference(response.getPriceDifference())
                    .paymentMethod(rescheduleRequest.getPaymentMethod())
                    .vnpayPaymentUrl(response.getPaymentUrl())
                    .paymentDeadline(LocalDateTime.now().plusMinutes(15)) // 15 phút để thanh toán
                    .confirmedSeatCode(confirmedSeatCode)
                    .seatReleased(true)
                    .seatConfirmed(false) // Will be confirmed after payment
                    .paymentCompleted(false)
                    .build();

            // Lưu saga state
            saveSagaState(sagaId, SagaStep.RESCHEDULE_PENDING_PAYMENT, sagaPayload);

            bookingDetail.setStatus(BookingDetailStatus.RESCHEDULE_IN_PROGRESS);
            bookingDetailRepository.save(bookingDetail);

            // Confirm seat mới trong flight service
            confirmNewSeat(rescheduleRequest.getNewFlightId(), confirmedSeatCode, bookingDetail.getBookingCode());

            log.info("Reschedule saga created - SagaId: {}, BookingDetailId: {}, PaymentRequired: {}",
                    sagaId, bookingDetail.getId(), response.getPriceDifference());

            // Set rescheduleHistoryId to null for payment-required cases
            response.setRescheduleHistoryId(null);
        } else {
            // Vé mới có giá bằng hoặc rẻ hơn - hoàn thành ngay không cần thanh toán/refund
            RescheduleFlightHistory history = RescheduleFlightHistory.builder()
                    .bookingDetail(bookingDetail)
                    .oldFlightId(oldFlightId)
                    .newFlightId(rescheduleRequest.getNewFlightId())
                    .oldSeatCode(oldSeatCode)
                    .newSeatCode(confirmedSeatCode)
                    .oldPrice(oldPrice)
                    .newPrice(response.getNewPrice())
                    .priceDifference(response.getPriceDifference())
                    .build();

            rescheduleFlightRepository.save(history);
            response.setRescheduleHistoryId(history.getId());

            bookingDetail.setStatus(BookingDetailStatus.RESCHEDULED);
            bookingDetailRepository.save(bookingDetail);

            // Confirm seat mới trong flight service
            confirmNewSeat(rescheduleRequest.getNewFlightId(), confirmedSeatCode, bookingDetail.getBookingCode());

            if (response.getPriceDifference() < 0) {
                log.info(
                        "Reschedule completed - BookingDetailId: {}, HistoryId: {}, Price difference: {} VND (no refund)",
                        bookingDetail.getId(), history.getId(), response.getPriceDifference());
            } else {
                log.info("Reschedule completed - BookingDetailId: {}, HistoryId: {}, No price difference",
                        bookingDetail.getId(), history.getId());
            }
        }
    }

    private void confirmNewSeat(UUID flightId, String seatCode, String bookingReference) {
        try {
            FsConfirmSeatsRequestDTO confirmRequest = FsConfirmSeatsRequestDTO.builder()
                    .bookingReference(bookingReference)
                    .seatCodes(Collections.singletonList(seatCode))
                    .build();

            FsConfirmSeatsResponseDTO response = flightClient.confirmSeats(flightId, confirmRequest);

            if (!"Success".equalsIgnoreCase(response.getStatus())) {
                log.error("Failed to confirm new seat {} for flight {}: {}", seatCode, flightId, response.getMessage());
                throw new BadRequestException("Failed to confirm new seat: " + response.getMessage());
            }

            log.info("Successfully confirmed new seat {} for flight {}", seatCode, flightId);
        } catch (Exception e) {
            log.error("Failed to confirm new seat {} for flight {}", seatCode, flightId, e);
            throw new BadRequestException("Failed to confirm new seat: " + e.getMessage());
        }
    }

    private void saveSagaState(UUID sagaId, SagaStep step,
            RescheduleFlightSagaOrchestrator.RescheduleFlightSagaPayload payload) {
        try {
            SagaState sagaState = SagaState.builder()
                    .sagaId(sagaId)
                    .currentStep(step)
                    .payloadJson(objectMapper.writeValueAsString(payload))
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            sagaStateRepository.save(sagaState);
            log.info("Saga state saved - SagaId: {}, Step: {}", sagaId, step);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize saga payload for sagaId: {}", sagaId, e);
            throw new BadRequestException("Failed to save reschedule state");
        }
    }

    /**
     * Complete reschedule after successful payment using saga
     * This method should be called from payment callback/webhook
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public UUID completeRescheduleAfterPayment(String uniqueBookingReference, String paymentTransactionId) {
        log.info("Completing reschedule after successful payment for booking: {}, transaction: {}",
                uniqueBookingReference, paymentTransactionId);

        // Find saga by booking reference
        SagaState sagaState = findSagaByBookingReference(uniqueBookingReference);
        if (sagaState == null) {
            throw new BadRequestException("No pending reschedule found for booking: " + uniqueBookingReference);
        }

        // Deserialize saga payload
        RescheduleFlightSagaOrchestrator.RescheduleFlightSagaPayload payload = deserializeSagaPayload(
                sagaState.getPayloadJson());

        // Verify booking detail is in correct state
        BookingDetail bookingDetail = bookingDetailRepository.findById(payload.getBookingDetailId())
                .orElseThrow(() -> new ResourceNotFoundException("BookingDetail", "id", payload.getBookingDetailId()));

        if (bookingDetail.getStatus() != BookingDetailStatus.RESCHEDULE_IN_PROGRESS) {
            throw new BadRequestException("BookingDetail is not in reschedule pending state");
        }

        // Create history record using saved saga data
        RescheduleFlightHistory history = RescheduleFlightHistory.builder()
                .bookingDetail(bookingDetail)
                .oldFlightId(payload.getOldFlightId())
                .newFlightId(payload.getNewFlightId())
                .oldSeatCode(payload.getOldSeatCode())
                .newSeatCode(payload.getNewSeatCode())
                .oldPrice(payload.getOldPrice())
                .newPrice(payload.getNewPrice())
                .priceDifference(payload.getPriceDifference())
                .build();

        rescheduleFlightRepository.save(history);

        // Update booking detail status to completed
        bookingDetail.setStatus(BookingDetailStatus.RESCHEDULED);
        bookingDetailRepository.save(bookingDetail);

        // Update saga state
        payload.setPaymentCompleted(true);
        updateSagaState(sagaState.getSagaId(), SagaStep.RESCHEDULE_COMPLETED, payload);

        log.info("Reschedule completed after payment - BookingDetailId: {}, HistoryId: {}, SagaId: {}",
                payload.getBookingDetailId(), history.getId(), sagaState.getSagaId());

        return history.getId();
    }

    private SagaState findSagaByBookingReference(String uniqueBookingReference) {
        // In a real implementation, you might need to add a query method to find saga
        // by booking reference
        // For now, we'll search through existing saga states
        return sagaStateRepository.findAll().stream()
                .filter(state -> {
                    try {
                        RescheduleFlightSagaOrchestrator.RescheduleFlightSagaPayload payload = deserializeSagaPayload(
                                state.getPayloadJson());
                        // The uniqueBookingReference is like "ORIGINAL_REF_RESCHEDULE_TIMESTAMP"
                        // We check if the unique ref starts with the original ref stored in the saga
                        // payload
                        return uniqueBookingReference.startsWith(payload.getBookingReference()) &&
                                state.getCurrentStep() == SagaStep.RESCHEDULE_PENDING_PAYMENT;
                    } catch (Exception e) {
                        return false;
                    }
                })
                .findFirst()
                .orElse(null);
    }

    private void updateSagaState(UUID sagaId, SagaStep step,
            RescheduleFlightSagaOrchestrator.RescheduleFlightSagaPayload payload) {
        try {
            SagaState sagaState = sagaStateRepository.findById(sagaId)
                    .orElseThrow(() -> new BadRequestException("Saga not found: " + sagaId));

            sagaState.setCurrentStep(step);
            sagaState.setPayloadJson(objectMapper.writeValueAsString(payload));
            sagaState.setUpdatedAt(LocalDateTime.now());

            sagaStateRepository.save(sagaState);
            log.info("Saga state updated - SagaId: {}, Step: {}", sagaId, step);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize saga payload for sagaId: {}", sagaId, e);
            throw new BadRequestException("Failed to update reschedule state");
        }
    }

    private RescheduleFlightSagaOrchestrator.RescheduleFlightSagaPayload deserializeSagaPayload(String payloadJson) {
        try {
            return objectMapper.readValue(payloadJson,
                    RescheduleFlightSagaOrchestrator.RescheduleFlightSagaPayload.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize saga payload: {}", payloadJson, e);
            throw new BadRequestException("Failed to read reschedule state");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getRescheduleHistory(String bookingReference, UUID userId) {
        log.info("Getting reschedule history for booking: {} and user: {}", bookingReference, userId);

        // Find all booking details for this booking reference and user
        List<BookingDetail> bookingDetails = bookingDetailRepository
                .findByBookingBookingReferenceAndBookingUserId(bookingReference, userId);

        if (bookingDetails.isEmpty()) {
            throw new ResourceNotFoundException("Booking not found with reference: " + bookingReference);
        }

        List<Map<String, Object>> historyList = new ArrayList<>();

        // Get reschedule history for each booking detail
        for (BookingDetail bookingDetail : bookingDetails) {
            List<RescheduleFlightHistory> histories = rescheduleFlightRepository
                    .findByBookingDetailOrderByCreatedAtDesc(bookingDetail);

            for (RescheduleFlightHistory history : histories) {
                Map<String, Object> historyItem = new HashMap<>();
                historyItem.put("id", history.getId());
                historyItem.put("bookingDetailId", history.getBookingDetail().getId());
                historyItem.put("oldFlightId", history.getOldFlightId());
                historyItem.put("newFlightId", history.getNewFlightId());
                historyItem.put("oldSeatCode", history.getOldSeatCode());
                historyItem.put("newSeatCode", history.getNewSeatCode());
                historyItem.put("oldPrice", history.getOldPrice());
                historyItem.put("newPrice", history.getNewPrice());
                historyItem.put("priceDifference", history.getPriceDifference());
                historyItem.put("rescheduleDate", history.getCreatedAt());

                // Add passenger information
                Passenger passenger = history.getBookingDetail().getPassenger();
                historyItem.put("passengerName", passenger.getFirstName() + " " + passenger.getLastName());
                historyItem.put("passengerTitle", passenger.getTitle());
                historyItem.put("passengerGender", passenger.getGender());
                historyItem.put("passengerNationality", passenger.getNationality());

                // Add flight information from booking detail
                historyItem.put("flightCode", history.getBookingDetail().getFlightCode());
                historyItem.put("originAirportCode", history.getBookingDetail().getOriginAirportCode());
                historyItem.put("destinationAirportCode", history.getBookingDetail().getDestinationAirportCode());
                historyItem.put("departureTime", history.getBookingDetail().getDepartureTime());
                historyItem.put("arrivalTime", history.getBookingDetail().getArrivalTime());

                historyList.add(historyItem);
            }
        }

        // Sort by reschedule date (newest first)
        historyList.sort(
                (a, b) -> ((LocalDateTime) b.get("rescheduleDate")).compareTo((LocalDateTime) a.get("rescheduleDate")));

        log.info("Found {} reschedule history records for booking: {}", historyList.size(), bookingReference);
        return historyList;
    }
}
