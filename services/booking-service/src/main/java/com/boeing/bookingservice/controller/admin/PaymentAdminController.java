package com.boeing.bookingservice.controller.admin;

import com.boeing.bookingservice.dto.response.ApiResponse;
import com.boeing.bookingservice.service.BookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/payments")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class PaymentAdminController {

    private final BookingService bookingService;

    /**
     * Get payment-based revenue statistics for admin dashboard
     */
    @GetMapping("/revenue")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getRevenueFromPayments(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {
        try {
            log.info("Admin fetching payment revenue from {} to {}", dateFrom, dateTo);

            Map<String, Object> revenueData = bookingService.getRevenueFromPayments(dateFrom, dateTo);

            return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                    .success(true)
                    .message("Payment revenue data retrieved successfully")
                    .data(revenueData)
                    .build());

        } catch (Exception e) {
            log.error("Error retrieving payment revenue: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.<Map<String, Object>>builder()
                            .success(false)
                            .message("Failed to retrieve payment revenue: " + e.getMessage())
                            .build());
        }
    }
}
