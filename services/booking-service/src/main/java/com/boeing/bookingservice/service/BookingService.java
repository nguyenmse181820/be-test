package com.boeing.bookingservice.service;

import com.boeing.bookingservice.dto.request.CreateBookingRequestDTO;
import com.boeing.bookingservice.dto.request.PassengerInfoDTO;
import com.boeing.bookingservice.dto.request.SeatSelectionDTO;
import com.boeing.bookingservice.dto.response.BookingFullDetailResponseDTO;
import com.boeing.bookingservice.dto.response.BookingInitiatedResponseDTO;
import com.boeing.bookingservice.dto.response.BookingSummaryDTO;
import com.boeing.bookingservice.model.entity.Booking;
import com.boeing.bookingservice.saga.command.BookingPassengerInfoDTO;
import com.boeing.bookingservice.integration.ls.dto.LsUserVoucherDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface BookingService {

    BookingInitiatedResponseDTO initiateBookingCreationSaga(CreateBookingRequestDTO createBookingRequest, UUID userId, String clientIpAddress);    @Transactional
    Booking createPendingBookingInDatabase(
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
            String clientIpAddress
    );

    void completeSagaInitiationWithPaymentUrl(UUID sagaId, String bookingReferenceDisplay, Double totalAmount, String vnpayPaymentUrl, LocalDateTime paymentDeadline);

    void failSagaInitiationBeforePaymentUrl(UUID sagaId, String bookingReferenceDisplay, String errorMessage);

    void cancelOverduePendingBooking(String bookingReference);

    void updateBookingStatusAndDetailsPostPayment(String bookingReference, boolean paymentSuccessfulAndSeatsConfirmed, String vnpayTransactionIdIfSuccessful);

    void handlePostPaymentWorkflow(String bookingReference);

    BookingFullDetailResponseDTO getBookingDetailsByReference(String bookingReference, UUID currentUserId, String currentUserRole);

    Page<BookingSummaryDTO> getUserBookings(UUID userId, Pageable pageable);

    List<LsUserVoucherDTO> getActiveUserVouchers(UUID userId);
}