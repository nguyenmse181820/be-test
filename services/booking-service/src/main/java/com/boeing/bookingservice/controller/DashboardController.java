package com.boeing.bookingservice.controller;

import com.boeing.bookingservice.dto.common.APIResponse;
import com.boeing.bookingservice.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Dashboard analytics and statistics")
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "Get comprehensive dashboard statistics")
    @GetMapping("/statistics")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<APIResponse> getDashboardStatistics() {
        Map<String, Object> statistics = dashboardService.getDashboardStatistics();
        return ResponseEntity.ok(APIResponse.builder()
                .success(true)
                .message("Dashboard statistics retrieved successfully")
                .data(statistics)
                .build());
    }

    @Operation(summary = "Get booking analytics")
    @GetMapping("/bookings/analytics")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<APIResponse> getBookingAnalytics(
            @Parameter(description = "Number of days to analyze") @RequestParam(defaultValue = "30") int days) {
        Map<String, Object> analytics = dashboardService.getBookingAnalytics(days);
        return ResponseEntity.ok(APIResponse.builder()
                .success(true)
                .message("Booking analytics retrieved successfully")
                .data(analytics)
                .build());
    }

    @Operation(summary = "Get revenue trends")
    @GetMapping("/revenue/trends")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<APIResponse> getRevenueTrends(
            @Parameter(description = "Start date") @RequestParam(required = false) LocalDate startDate,
            @Parameter(description = "End date") @RequestParam(required = false) LocalDate endDate) {
        Map<String, Object> trends = dashboardService.getRevenueTrends(startDate, endDate);
        return ResponseEntity.ok(APIResponse.builder()
                .success(true)
                .message("Revenue trends retrieved successfully")
                .data(trends)
                .build());
    }

    @Operation(summary = "Get customer analytics")
    @GetMapping("/customers/analytics")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<APIResponse> getCustomerAnalytics() {
        Map<String, Object> analytics = dashboardService.getCustomerAnalytics();
        return ResponseEntity.ok(APIResponse.builder()
                .success(true)
                .message("Customer analytics retrieved successfully")
                .data(analytics)
                .build());
    }

    @Operation(summary = "Get payment analytics")
    @GetMapping("/payments/analytics")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<APIResponse> getPaymentAnalytics(
            @Parameter(description = "Number of days to analyze") @RequestParam(defaultValue = "30") int days) {
        Map<String, Object> analytics = dashboardService.getPaymentAnalytics(days);
        return ResponseEntity.ok(APIResponse.builder()
                .success(true)
                .message("Payment analytics retrieved successfully")
                .data(analytics)
                .build());
    }

    @Operation(summary = "Get recent activities")
    @GetMapping("/activities/recent")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<APIResponse> getRecentActivities(
            @Parameter(description = "Number of activities to retrieve") @RequestParam(defaultValue = "10") int limit) {
        Map<String, Object> activities = dashboardService.getRecentActivities(limit);
        return ResponseEntity.ok(APIResponse.builder()
                .success(true)
                .message("Recent activities retrieved successfully")
                .data(activities)
                .build());
    }

    @Operation(summary = "Get booking funnel analytics")
    @GetMapping("/bookings/funnel")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<APIResponse> getBookingFunnel(
            @Parameter(description = "Number of days to analyze") @RequestParam(defaultValue = "30") int days) {
        Map<String, Object> funnel = dashboardService.getBookingFunnel(days);
        return ResponseEntity.ok(APIResponse.builder()
                .success(true)
                .message("Booking funnel analytics retrieved successfully")
                .data(funnel)
                .build());
    }

    @Operation(summary = "Get real-time metrics")
    @GetMapping("/realtime")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<APIResponse> getRealTimeMetrics() {
        Map<String, Object> metrics = dashboardService.getRealTimeMetrics();
        return ResponseEntity.ok(APIResponse.builder()
                .success(true)
                .message("Real-time metrics retrieved successfully")
                .data(metrics)
                .build());
    }
}