package com.boeing.bookingservice.service;

import com.boeing.bookingservice.dto.request.BaggageAddonRequestDTO;
import com.boeing.bookingservice.dto.request.CreateBookingRequestDTO;
import com.boeing.bookingservice.dto.request.PassengerInfoDTO;
import com.boeing.bookingservice.dto.request.SeatSelectionDTO;
import com.boeing.bookingservice.dto.response.BookingFullDetailResponseDTO;
import com.boeing.bookingservice.dto.response.BookingInitiatedResponseDTO;
import com.boeing.bookingservice.dto.response.BookingSummaryDTO;
import com.boeing.bookingservice.integration.ls.dto.LsUserVoucherDTO;
import com.boeing.bookingservice.model.entity.Booking;
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
    BookingInitiatedResponseDTO initiateBookingCreationSaga(CreateBookingRequestDTO createBookingRequest,
                                                            UUID userId, String clientIpAddress);

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
            String clientIpAddress,
            List<BaggageAddonRequestDTO> baggageAddons);

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
            String clientIpAddress,
            List<List<Map<String, Object>>> seatPricingByFlight,
            List<BaggageAddonRequestDTO> baggageAddons);

    void completeSagaInitiationWithPaymentUrl(UUID sagaId, String bookingReferenceDisplay, Double totalAmount,
                                              String vnpayPaymentUrl, LocalDateTime paymentDeadline);

    void failSagaInitiationBeforePaymentUrl(UUID sagaId, String bookingReferenceDisplay, String errorMessage);

    void cancelOverduePendingBooking(String bookingReference);

    void updateBookingStatusAndDetailsPostPayment(String bookingReference,
                                                  boolean paymentSuccessfulAndSeatsConfirmed, String vnpayTransactionIdIfSuccessful);

    void handlePostPaymentWorkflow(String bookingReference);

    BookingFullDetailResponseDTO getBookingDetailsByReference(String bookingReference, UUID currentUserId,
                                                              String currentUserRole);

    /**
     * Retrieves all bookings for a user with optional status filtering
     *
     * @param userId   The ID of the user whose bookings to retrieve
     * @param status   Optional status to filter bookings by
     * @param pageable Pagination information
     * @return A page of booking summaries matching the criteria
     */
    Page<BookingSummaryDTO> getUserBookings(UUID userId, String status, Pageable pageable);

    /**
     * Retrieves all bookings for a user (without status filtering)
     *
     * @param userId   The ID of the user whose bookings to retrieve
     * @param pageable Pagination information
     * @return A page of booking summaries
     */
    Page<BookingSummaryDTO> getUserBookings(UUID userId, Pageable pageable);

    List<LsUserVoucherDTO> getActiveUserVouchers(UUID userId);

    void cancelBooking(UUID bookingId, String reason);

    /**
     * Retrieves booking statistics for a user, including counts for each status
     *
     * @param userId The ID of the user whose booking statistics to retrieve
     * @return Map with booking status counts and total value
     */
    Map<String, Object> getUserBookingStatistics(UUID userId);

    // Admin-specific methods

    /**
     * Retrieves all bookings for admin with advanced filtering
     *
     * @param pageable       Pagination information
     * @param status         Optional status to filter bookings by
     * @param searchTerm     Optional search term for booking reference or flight
     *                       code
     * @param userId         Optional user ID filter
     * @param flightCode     Optional flight code filter
     * @param totalAmountMin Optional minimum total amount filter
     * @param totalAmountMax Optional maximum total amount filter
     * @param dateFrom       Optional start date filter
     * @param dateTo         Optional end date filter
     * @return A page of booking summaries matching the criteria
     */
    Page<BookingSummaryDTO> getAllBookingsForAdmin(
            Pageable pageable,
            String status,
            String searchTerm,
            UUID userId,
            String flightCode,
            Double totalAmountMin,
            Double totalAmountMax,
            String dateFrom,
            String dateTo);

    /**
     * Retrieves booking details by reference for admin (no user ownership check)
     *
     * @param bookingReference The booking reference to retrieve details for
     * @return Detailed booking information
     */
    BookingFullDetailResponseDTO getBookingDetailsByReferenceForAdmin(String bookingReference);

    /**
     * Retrieves comprehensive booking statistics for admin dashboard
     *
     * @param dateFrom Optional start date filter
     * @param dateTo   Optional end date filter
     * @return Map containing various booking statistics and metrics
     */
    Map<String, Object> getBookingStatisticsForAdmin(String dateFrom, String dateTo);

    /**
     * Retrieves daily revenue data for admin analytics
     *
     * @param dateFrom Optional start date filter
     * @param dateTo   Optional end date filter
     * @return Map containing daily revenue breakdown
     */
    Map<String, Object> getDailyRevenueForAdmin(String dateFrom, String dateTo);

    /**
     * Retrieves top performing routes for admin analytics
     *
     * @param dateFrom Optional start date filter
     * @param dateTo   Optional end date filter
     * @param limit    Maximum number of routes to return
     * @return Map containing top routes by booking count and revenue
     */
    Map<String, Object> getTopRoutesForAdmin(String dateFrom, String dateTo, int limit);

    /**
     * Updates booking status for admin management
     *
     * @param bookingReference The booking reference to update
     * @param newStatus        The new status to set
     * @param reason           Optional reason for the status change
     */
    void updateBookingStatusForAdmin(String bookingReference, String newStatus, String reason);

    /**
     * Exports booking data for admin reporting
     *
     * @param status   Optional status filter
     * @param dateFrom Optional start date filter
     * @param dateTo   Optional end date filter
     * @param format   Export format (csv, excel, etc.)
     * @return URL or path to the exported file
     */
    String exportBookingsForAdmin(String status, String dateFrom, String dateTo, String format);

    /**
     * Gets enhanced booking statistics including paid status and payment-based
     * revenue
     *
     * @param dateFrom Optional start date filter
     * @param dateTo   Optional end date filter
     * @return Map containing enhanced statistics with paid count and payment-based
     * revenue
     */
    Map<String, Object> getEnhancedBookingStatisticsForAdmin(String dateFrom, String dateTo);

    /**
     * Gets revenue calculated from actual payments (includes reschedule fees,
     * excludes refunds)
     *
     * @param dateFrom Optional start date filter
     * @param dateTo   Optional end date filter
     * @return Map containing payment-based revenue information
     */
    Map<String, Object> getRevenueFromPayments(String dateFrom, String dateTo);
}