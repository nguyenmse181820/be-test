package com.boeing.bookingservice.controller.admin;

import com.boeing.bookingservice.dto.response.ApiResponse;
import com.boeing.bookingservice.dto.response.BookingFullDetailResponseDTO;
import com.boeing.bookingservice.dto.response.BookingSummaryDTO;
import com.boeing.bookingservice.security.AuthenticatedUserPrincipal;
import com.boeing.bookingservice.service.BookingService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * REST Controller for booking administration functionality.
 * Provides admin-specific booking management operations.
 */
@RestController
@RequestMapping({ "/api/v1/admin/bookings", "/booking-service/api/v1/admin/bookings" })
@RequiredArgsConstructor
@Slf4j
public class BookingAdminController {

        private final BookingService bookingService;

        /**
         * Retrieves all bookings with advanced filtering for admin.
         * 
         * @param pageable       Pagination parameters
         * @param status         Optional booking status filter
         * @param searchTerm     Optional search term for booking reference or flight
         *                       code
         * @param userId         Optional user ID filter
         * @param flightCode     Optional flight code filter
         * @param totalAmountMin Optional minimum total amount filter
         * @param totalAmountMax Optional maximum total amount filter
         * @param dateFrom       Optional start date filter (YYYY-MM-DD)
         * @param dateTo         Optional end date filter (YYYY-MM-DD)
         * @return Paginated list of booking summaries
         */
        @GetMapping
        @PreAuthorize("hasAnyAuthority('ADMIN', 'STAFF')")
        public ResponseEntity<ApiResponse<Page<BookingSummaryDTO>>> getAllBookings(
                        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
                        @RequestParam(required = false) String status,
                        @RequestParam(required = false) String searchTerm,
                        @RequestParam(required = false) UUID userId,
                        @RequestParam(required = false) String flightCode,
                        @RequestParam(required = false) Double totalAmountMin,
                        @RequestParam(required = false) Double totalAmountMax,
                        @RequestParam(required = false) String dateFrom,
                        @RequestParam(required = false) String dateTo) {

                try {
                        log.info("Admin fetching all bookings with filters - status: {}, searchTerm: {}, userId: {}",
                                        status, searchTerm, userId);

                        Page<BookingSummaryDTO> bookings = bookingService.getAllBookingsForAdmin(
                                        pageable, status, searchTerm, userId, flightCode,
                                        totalAmountMin, totalAmountMax, dateFrom, dateTo);

                        return ResponseEntity.ok(ApiResponse.<Page<BookingSummaryDTO>>builder()
                                        .success(true)
                                        .message("Bookings retrieved successfully")
                                        .data(bookings)
                                        .build());

                } catch (Exception e) {
                        log.error("Error fetching bookings for admin: {}", e.getMessage(), e);
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(ApiResponse.<Page<BookingSummaryDTO>>builder()
                                                        .success(false)
                                                        .message("Failed to fetch bookings: " + e.getMessage())
                                                        .build());
                }
        }

        /**
         * Retrieves detailed booking information by reference for admin.
         * 
         * @param bookingReference The booking reference
         * @return Detailed booking information
         */
        @GetMapping("/{bookingReference}")
        @PreAuthorize("hasAnyAuthority('ADMIN', 'STAFF')")
        public ResponseEntity<ApiResponse<BookingFullDetailResponseDTO>> getBookingDetails(
                        @PathVariable String bookingReference) {

                try {
                        log.info("Admin fetching booking details for reference: {}", bookingReference);

                        BookingFullDetailResponseDTO bookingDetails = bookingService
                                        .getBookingDetailsByReferenceForAdmin(bookingReference);

                        return ResponseEntity.ok(ApiResponse.<BookingFullDetailResponseDTO>builder()
                                        .success(true)
                                        .message("Booking details retrieved successfully")
                                        .data(bookingDetails)
                                        .build());

                } catch (Exception e) {
                        log.error("Error fetching booking details for admin: {}", e.getMessage(), e);
                        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                        .body(ApiResponse.<BookingFullDetailResponseDTO>builder()
                                                        .success(false)
                                                        .message("Failed to fetch booking details: " + e.getMessage())
                                                        .build());
                }
        }

        /**
         * Retrieves comprehensive booking statistics for admin dashboard.
         * 
         * @param dateFrom Optional start date filter (YYYY-MM-DD)
         * @param dateTo   Optional end date filter (YYYY-MM-DD)
         * @return Map containing various booking statistics and metrics
         */
        @GetMapping("/statistics")
        @PreAuthorize("hasAnyAuthority('ADMIN', 'STAFF')")
        public ResponseEntity<ApiResponse<Map<String, Object>>> getBookingStatistics(
                        @RequestParam(required = false) String dateFrom,
                        @RequestParam(required = false) String dateTo) {

                try {
                        log.info("Admin fetching booking statistics from {} to {}", dateFrom, dateTo);

                        Map<String, Object> statistics = bookingService.getBookingStatisticsForAdmin(dateFrom, dateTo);

                        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                                        .success(true)
                                        .message("Booking statistics retrieved successfully")
                                        .data(statistics)
                                        .build());

                } catch (Exception e) {
                        log.error("Error fetching booking statistics for admin: {}", e.getMessage(), e);
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(ApiResponse.<Map<String, Object>>builder()
                                                        .success(false)
                                                        .message("Failed to fetch booking statistics: "
                                                                        + e.getMessage())
                                                        .build());
                }
        }

        /**
         * Retrieves daily revenue data for admin analytics.
         * 
         * @param dateFrom Optional start date filter (YYYY-MM-DD)
         * @param dateTo   Optional end date filter (YYYY-MM-DD)
         * @return Map containing daily revenue breakdown
         */
        @GetMapping("/revenue/daily")
        @PreAuthorize("hasAnyAuthority('ADMIN', 'STAFF')")
        public ResponseEntity<ApiResponse<Map<String, Object>>> getDailyRevenue(
                        @RequestParam(required = false) String dateFrom,
                        @RequestParam(required = false) String dateTo) {

                try {
                        log.info("Admin fetching daily revenue from {} to {}", dateFrom, dateTo);

                        Map<String, Object> dailyRevenue = bookingService.getDailyRevenueForAdmin(dateFrom, dateTo);

                        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                                        .success(true)
                                        .message("Daily revenue data retrieved successfully")
                                        .data(dailyRevenue)
                                        .build());

                } catch (Exception e) {
                        log.error("Error fetching daily revenue for admin: {}", e.getMessage(), e);
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(ApiResponse.<Map<String, Object>>builder()
                                                        .success(false)
                                                        .message("Failed to fetch daily revenue: " + e.getMessage())
                                                        .build());
                }
        }

        /**
         * Retrieves top performing routes for admin analytics.
         * 
         * @param dateFrom Optional start date filter (YYYY-MM-DD)
         * @param dateTo   Optional end date filter (YYYY-MM-DD)
         * @param limit    Maximum number of routes to return (default: 10)
         * @return Map containing top routes by booking count and revenue
         */
        @GetMapping("/top-routes")
        @PreAuthorize("hasAnyAuthority('ADMIN', 'STAFF')")
        public ResponseEntity<ApiResponse<Map<String, Object>>> getTopRoutes(
                        @RequestParam(required = false) String dateFrom,
                        @RequestParam(required = false) String dateTo,
                        @RequestParam(defaultValue = "10") int limit) {

                try {
                        log.info("Admin fetching top routes from {} to {} with limit {}", dateFrom, dateTo, limit);

                        Map<String, Object> topRoutes = bookingService.getTopRoutesForAdmin(dateFrom, dateTo, limit);

                        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                                        .success(true)
                                        .message("Top routes data retrieved successfully")
                                        .data(topRoutes)
                                        .build());

                } catch (Exception e) {
                        log.error("Error fetching top routes for admin: {}", e.getMessage(), e);
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(ApiResponse.<Map<String, Object>>builder()
                                                        .success(false)
                                                        .message("Failed to fetch top routes: " + e.getMessage())
                                                        .build());
                }
        }

        /**
         * Updates booking status for admin management.
         * 
         * @param bookingReference The booking reference to update
         * @param newStatus        The new status to set
         * @param reason           Optional reason for the status change
         * @param authentication   Admin authentication details
         * @return Success/failure response
         */
        @PutMapping("/{bookingReference}/status")
        @PreAuthorize("hasAnyAuthority('ADMIN', 'STAFF')")
        public ResponseEntity<ApiResponse<String>> updateBookingStatus(
                        @PathVariable String bookingReference,
                        @RequestParam String newStatus,
                        @RequestParam(required = false) String reason,
                        Authentication authentication) {

                try {
                        String adminId = getUserIdFromAuthentication(authentication).toString();
                        log.info("Admin {} updating booking {} status to {} with reason: {}",
                                        adminId, bookingReference, newStatus, reason);

                        bookingService.updateBookingStatusForAdmin(bookingReference, newStatus, reason);

                        return ResponseEntity.ok(ApiResponse.<String>builder()
                                        .success(true)
                                        .message("Booking status updated successfully")
                                        .data("Status updated to: " + newStatus)
                                        .build());

                } catch (Exception e) {
                        log.error("Error updating booking status for admin: {}", e.getMessage(), e);
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                        .body(ApiResponse.<String>builder()
                                                        .success(false)
                                                        .message("Failed to update booking status: " + e.getMessage())
                                                        .build());
                }
        }

        /**
         * Exports booking data for admin reporting.
         * 
         * @param status   Optional status filter
         * @param dateFrom Optional start date filter (YYYY-MM-DD)
         * @param dateTo   Optional end date filter (YYYY-MM-DD)
         * @param format   Export format (csv, excel, etc.)
         * @param response HTTP response for file download
         * @return Export file URL or direct file download
         */
        @GetMapping("/export")
        @PreAuthorize("hasAnyAuthority('ADMIN', 'STAFF')")
        public ResponseEntity<ApiResponse<String>> exportBookings(
                        @RequestParam(required = false) String status,
                        @RequestParam(required = false) String dateFrom,
                        @RequestParam(required = false) String dateTo,
                        @RequestParam(defaultValue = "csv") String format,
                        HttpServletResponse response) throws IOException {

                try {
                        log.info("Admin exporting bookings - status: {}, from: {}, to: {}, format: {}",
                                        status, dateFrom, dateTo, format);

                        String exportUrl = bookingService.exportBookingsForAdmin(status, dateFrom, dateTo, format);

                        return ResponseEntity.ok(ApiResponse.<String>builder()
                                        .success(true)
                                        .message("Export initiated successfully")
                                        .data(exportUrl)
                                        .build());

                } catch (Exception e) {
                        log.error("Error exporting bookings for admin: {}", e.getMessage(), e);
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(ApiResponse.<String>builder()
                                                        .success(false)
                                                        .message("Failed to export bookings: " + e.getMessage())
                                                        .build());
                }
        }

        /**
         * Retrieves enhanced booking statistics including paid status and payment-based
         * revenue.
         * 
         * @param dateFrom Optional start date filter (YYYY-MM-DD)
         * @param dateTo   Optional end date filter (YYYY-MM-DD)
         * @return Map containing enhanced statistics with paid count and payment-based
         *         revenue
         */
        @GetMapping("/enhanced-statistics")
        @PreAuthorize("hasAnyAuthority('ADMIN', 'STAFF')")
        public ResponseEntity<ApiResponse<Map<String, Object>>> getEnhancedBookingStatistics(
                        @RequestParam(required = false) String dateFrom,
                        @RequestParam(required = false) String dateTo) {

                try {
                        log.info("Admin fetching enhanced booking statistics from {} to {}", dateFrom, dateTo);

                        Map<String, Object> enhancedStatistics = bookingService
                                        .getEnhancedBookingStatisticsForAdmin(dateFrom, dateTo);

                        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                                        .success(true)
                                        .message("Enhanced booking statistics retrieved successfully")
                                        .data(enhancedStatistics)
                                        .build());

                } catch (Exception e) {
                        log.error("Error fetching enhanced booking statistics for admin: {}", e.getMessage(), e);
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(ApiResponse.<Map<String, Object>>builder()
                                                        .success(false)
                                                        .message("Failed to fetch enhanced booking statistics: "
                                                                        + e.getMessage())
                                                        .build());
                }
        }

        /**
         * Extracts user ID from authentication context.
         * 
         * @param authentication Spring Security authentication object
         * @return User UUID
         * @throws AccessDeniedException if authentication is invalid
         */
        private UUID getUserIdFromAuthentication(Authentication authentication) {
                if (authentication == null || !authentication.isAuthenticated()) {
                        throw new AccessDeniedException("User is not authenticated.");
                }
                Object principal = authentication.getPrincipal();
                if (principal instanceof AuthenticatedUserPrincipal) {
                        return ((AuthenticatedUserPrincipal) principal).getUserIdAsUUID();
                }
                throw new AccessDeniedException("Cannot determine user ID from authentication principal.");
        }
}
