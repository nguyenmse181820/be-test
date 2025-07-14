package com.boeing.bookingservice.controller;

import com.boeing.bookingservice.dto.request.CreateBookingRequestDTO;
import com.boeing.bookingservice.dto.request.RescheduleFlightRequestDTO;
import com.boeing.bookingservice.dto.response.ApiResponse;
import com.boeing.bookingservice.dto.response.BookingFullDetailResponseDTO;
import com.boeing.bookingservice.dto.response.BookingInitiatedResponseDTO;
import com.boeing.bookingservice.dto.response.BookingSummaryDTO;
import com.boeing.bookingservice.dto.response.RescheduleFlightResponseDTO;
import com.boeing.bookingservice.integration.ls.dto.LsUserVoucherDTO;
import com.boeing.bookingservice.security.AuthenticatedUserPrincipal;
import com.boeing.bookingservice.service.BookingService;
import com.boeing.bookingservice.service.RescheduleService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping({ "/api/v1/bookings", "/booking-service/api/v1/bookings" })
@RequiredArgsConstructor
@Slf4j
public class BookingController {

    private final BookingService bookingService;
    private final RescheduleService rescheduleService;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('USER')")
    public ResponseEntity<ApiResponse<BookingInitiatedResponseDTO>> initiateBooking(
            @Valid @RequestBody CreateBookingRequestDTO createBookingRequest,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        UUID userId = getUserIdFromAuthentication(authentication);
        String clientIpAddress = getClientIpAddress(httpRequest);

        BookingInitiatedResponseDTO response = bookingService.initiateBookingCreationSaga(
                createBookingRequest,
                userId,
                clientIpAddress);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.<BookingInitiatedResponseDTO>builder()
                        .success(true)
                        .message("Booking request accepted and is being processed.")
                        .data(response)
                        .build());
    }

    @GetMapping("/{bookingReference}")
    @PreAuthorize("hasAnyAuthority('USER', 'ADMIN')")
    public ResponseEntity<ApiResponse<BookingFullDetailResponseDTO>> getBookingDetails(
            @PathVariable String bookingReference) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UUID currentUserId = getUserIdFromAuthentication(authentication);
        String currentUserRole = getUserRoleFromAuthentication(authentication);
        BookingFullDetailResponseDTO bookingDetails = bookingService.getBookingDetailsByReference(bookingReference,
                currentUserId, currentUserRole);

        return ResponseEntity.ok(ApiResponse.<BookingFullDetailResponseDTO>builder()
                .success(true)
                .message("Booking details retrieved successfully.")
                .data(bookingDetails)
                .build());
    }

    @GetMapping("/user")
    @PreAuthorize("hasAuthority('USER')")
    public ResponseEntity<ApiResponse<Page<BookingSummaryDTO>>> getUserBookings(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) String status) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UUID userId = getUserIdFromAuthentication(authentication);

        Page<BookingSummaryDTO> userBookings;
        if (status != null && !status.isEmpty()) {
            userBookings = bookingService.getUserBookings(userId, status, pageable);
        } else {
            userBookings = bookingService.getUserBookings(userId, pageable);
        }

        return ResponseEntity.ok(ApiResponse.<Page<BookingSummaryDTO>>builder()
                .success(true)
                .message("User booking history retrieved successfully.")
                .data(userBookings)
                .build());
    }

    @GetMapping("/user/vouchers")
    @PreAuthorize("hasAuthority('USER')")
    public ResponseEntity<ApiResponse<List<LsUserVoucherDTO>>> getActiveUserVouchers() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UUID userId = getUserIdFromAuthentication(authentication);

        try {
            List<LsUserVoucherDTO> activeVouchers = bookingService.getActiveUserVouchers(userId);
            return ResponseEntity.ok(ApiResponse.<List<LsUserVoucherDTO>>builder()
                    .success(true)
                    .message("Active user vouchers retrieved successfully.")
                    .data(activeVouchers)
                    .build());
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.<List<LsUserVoucherDTO>>builder()
                    .success(false)
                    .message("Vouchers are temporarily unavailable. Please try again later.")
                    .data(Collections.emptyList())
                    .build());
        }
    }

    @PostMapping("/{bookingDetailId}/reschedule")
    @PreAuthorize("hasAuthority('USER')")
    public ResponseEntity<ApiResponse<RescheduleFlightResponseDTO>> rescheduleBookingDetail(
            @PathVariable UUID bookingDetailId,
            @Valid @RequestBody RescheduleFlightRequestDTO rescheduleRequest,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        UUID userId = getUserIdFromAuthentication(authentication);

        // Set default payment method if not provided
        if (rescheduleRequest.getPaymentMethod() == null || rescheduleRequest.getPaymentMethod().trim().isEmpty()) {
            rescheduleRequest.setPaymentMethod("VNPAY_BANKTRANSFER");
        }

        // Extract client IP address from request
        String clientIpAddress = getClientIpAddress(httpRequest);

        try {
            RescheduleFlightResponseDTO response = rescheduleService.rescheduleBookingDetail(bookingDetailId,
                    rescheduleRequest, userId, clientIpAddress);

            return ResponseEntity.ok(ApiResponse.<RescheduleFlightResponseDTO>builder()
                    .success(true)
                    .message("Flight reschedule processed successfully.")
                    .data(response)
                    .build());
        } catch (Exception e) {
            log.error("Error processing reschedule request for booking detail {}: {}", bookingDetailId, e.getMessage(),
                    e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<RescheduleFlightResponseDTO>builder()
                            .success(false)
                            .message("Failed to process reschedule request: " + e.getMessage())
                            .data(null)
                            .build());
        }
    }

    @GetMapping("/{bookingDetailId}/reschedule/eligibility")
    @PreAuthorize("hasAuthority('USER')")
    public ResponseEntity<ApiResponse<Boolean>> checkRescheduleEligibility(
            @PathVariable UUID bookingDetailId,
            Authentication authentication) {

        UUID userId = getUserIdFromAuthentication(authentication);

        try {
            boolean canReschedule = rescheduleService.canReschedule(bookingDetailId, userId);

            return ResponseEntity.ok(ApiResponse.<Boolean>builder()
                    .success(true)
                    .message(canReschedule ? "Booking detail is eligible for reschedule."
                            : "Booking detail is not eligible for reschedule.")
                    .data(canReschedule)
                    .build());
        } catch (Exception e) {
            log.error("Error checking reschedule eligibility for booking detail {}: {}", bookingDetailId,
                    e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<Boolean>builder()
                            .success(false)
                            .message("Failed to check reschedule eligibility: " + e.getMessage())
                            .data(false)
                            .build());
        }
    }

    @GetMapping("/user/statistics")
    @PreAuthorize("hasAuthority('USER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUserBookingStatistics() {
        try {
            log.info("Controller: Fetching booking statistics for user");
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication == null) {
                log.error("Controller: Authentication is null in getUserBookingStatistics");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.<Map<String, Object>>builder()
                                .success(false)
                                .message("Authentication required.")
                                .build());
            }

            log.debug("Controller: Authentication details - isAuthenticated: {}, principal type: {}, authorities: {}",
                    authentication.isAuthenticated(),
                    authentication.getPrincipal() != null ? authentication.getPrincipal().getClass().getName() : "null",
                    authentication.getAuthorities());

            UUID userId;
            try {
                userId = getUserIdFromAuthentication(authentication);
                log.info("Controller: Retrieved user ID: {} for statistics", userId);
            } catch (AccessDeniedException e) {
                log.error("Controller: Failed to get userId from authentication: {}", e.getMessage());
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.<Map<String, Object>>builder()
                                .success(false)
                                .message("Failed to authenticate user: " + e.getMessage())
                                .build());
            }

            Map<String, Object> statistics;
            try {
                statistics = bookingService.getUserBookingStatistics(userId);
                log.info("Controller: Successfully retrieved statistics for user: {}", userId);
            } catch (Exception e) {
                log.error("Controller: Error while fetching statistics for user {}: {}", userId, e.getMessage(), e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ApiResponse.<Map<String, Object>>builder()
                                .success(false)
                                .message("Failed to retrieve booking statistics: " + e.getMessage())
                                .build());
            }

            return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                    .success(true)
                    .message("Booking statistics retrieved successfully.")
                    .data(statistics)
                    .build());
        } catch (Exception e) {
            log.error("Controller: Unexpected error in getUserBookingStatistics: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.<Map<String, Object>>builder()
                            .success(false)
                            .message("An unexpected error occurred while retrieving booking statistics.")
                            .build());
        }
    }

    @GetMapping("/{bookingReference}/reschedule-history")
    @PreAuthorize("hasAuthority('USER')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getRescheduleHistory(
            @PathVariable String bookingReference,
            Authentication authentication) {

        UUID userId = getUserIdFromAuthentication(authentication);

        try {
            List<Map<String, Object>> history = rescheduleService.getRescheduleHistory(bookingReference, userId);

            return ResponseEntity.ok(ApiResponse.<List<Map<String, Object>>>builder()
                    .success(true)
                    .message("Reschedule history retrieved successfully.")
                    .data(history)
                    .build());
        } catch (Exception e) {
            log.error("Error fetching reschedule history for booking {}: {}", bookingReference, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<List<Map<String, Object>>>builder()
                            .success(false)
                            .message("Failed to fetch reschedule history: " + e.getMessage())
                            .data(Collections.emptyList())
                            .build());
        }
    }

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

    private String getUserRoleFromAuthentication(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || authentication.getAuthorities() == null) {
            throw new AccessDeniedException("User is not authenticated or has no authorities.");
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .map(role -> role.startsWith("ROLE_") ? role.substring(5) : role)
                .orElse("UNKNOWN");
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String remoteAddr = "";
        if (request != null) {
            remoteAddr = request.getHeader("X-FORWARDED-FOR");
            if (remoteAddr == null || "".equals(remoteAddr)) {
                remoteAddr = request.getRemoteAddr();
            } else {
                remoteAddr = remoteAddr.split(",")[0].trim();
            }
        }
        return remoteAddr;
    }
}