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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for managing booking-related operations.
 * Transaction management is handled at the implementation level.
 */
public interface BookingService {

    /**
     * Initiates the booking creation saga process
     */
    BookingInitiatedResponseDTO initiateBookingCreationSaga(CreateBookingRequestDTO createBookingRequest, UUID userId, String clientIpAddress);
    
    /**
     * Creates a pending booking in the database for a single flight segment
     */
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
    
    /**
     * Creates a pending booking in the database for multiple flight segments
     */
    Booking createPendingMultiSegmentBookingInDatabase(
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