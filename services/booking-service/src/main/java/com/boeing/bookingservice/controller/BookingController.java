package com.boeing.bookingservice.controller;

import com.boeing.bookingservice.dto.request.CreateBookingRequestDTO;
import com.boeing.bookingservice.dto.response.ApiResponse;
import com.boeing.bookingservice.dto.response.BookingFullDetailResponseDTO;
import com.boeing.bookingservice.dto.response.BookingInitiatedResponseDTO;
import com.boeing.bookingservice.dto.response.BookingSummaryDTO;
import com.boeing.bookingservice.integration.ls.dto.LsUserVoucherDTO;
import com.boeing.bookingservice.security.AuthenticatedUserPrincipal;
import com.boeing.bookingservice.service.BookingService;
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
import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/bookings", "/booking-service/api/v1/bookings"})
@RequiredArgsConstructor
@Slf4j
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    @PreAuthorize("hasAuthority('USER')")
    public ResponseEntity<ApiResponse<BookingInitiatedResponseDTO>> initiateBooking(
            @Valid @RequestBody CreateBookingRequestDTO createBookingRequest,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        UUID userId = getUserIdFromAuthentication(authentication);
        String clientIpAddress = getClientIpAddress(httpRequest);

        BookingInitiatedResponseDTO response = bookingService.initiateBookingCreationSaga(
                createBookingRequest,
                userId,
                clientIpAddress
        );
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
        BookingFullDetailResponseDTO bookingDetails = bookingService.getBookingDetailsByReference(bookingReference, currentUserId, currentUserRole);

        return ResponseEntity.ok(ApiResponse.<BookingFullDetailResponseDTO>builder()
                .success(true)
                .message("Booking details retrieved successfully.")
                .data(bookingDetails)
                .build());
    }

    @GetMapping("/user")
    @PreAuthorize("hasAuthority('USER')")
    public ResponseEntity<ApiResponse<Page<BookingSummaryDTO>>> getUserBookings(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UUID userId = getUserIdFromAuthentication(authentication);

        Page<BookingSummaryDTO> userBookings = bookingService.getUserBookings(userId, pageable);

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