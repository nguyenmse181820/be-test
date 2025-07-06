package com.boeing.loyalty.controller;

import com.boeing.loyalty.dto.APIResponse;
import com.boeing.loyalty.dto.membership.EarnPointsRequestDTO;
import com.boeing.loyalty.service.LoyaltyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/loyalty")
@RequiredArgsConstructor
@Tag(name = "Loyalty Service", description = "APIs for managing loyalty points and vouchers")
public class LoyaltyController {

    private final LoyaltyService loyaltyService;

    @Operation(summary = "Get all vouchers for a user", description = "Retrieves all available vouchers for a specific user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved vouchers"),
            @ApiResponse(responseCode = "400", description = "User not found")
    })
    @GetMapping("/{userId}/vouchers")
    public ResponseEntity<APIResponse> getAllVoucher(
            @Parameter(description = "User ID", required = true) @PathVariable UUID userId) {
        return ResponseEntity.ok(APIResponse.builder()
                .data(loyaltyService.getAllVoucher(userId))
                .build());
    }

    @Operation(summary = "Earn loyalty points", description = "Adds loyalty points to a user's account based on their spending")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Points earned successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "400", description = "User not found")
    })
    @PostMapping("/points/earn")
    public ResponseEntity<APIResponse> earnPoints(
            @Parameter(description = "Earn points request details", required = true)
            @RequestBody EarnPointsRequestDTO requestDTO) {
        return ResponseEntity.ok(APIResponse.builder()
                .data(loyaltyService.earnPoints(requestDTO))
                .build());
    }

    @Operation(summary = "Use a voucher", description = "Marks a voucher as used and applies its benefits")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Voucher used successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid or already used voucher"),
            @ApiResponse(responseCode = "400", description = "Voucher not found")
    })
    @PostMapping("/vouchers/{voucherCode}/use")
    public ResponseEntity<APIResponse> useVoucher(
            @Parameter(description = "Voucher code to use", required = true)
            @PathVariable String voucherCode) {
        return ResponseEntity.ok(APIResponse.builder()
                .data(loyaltyService.useVoucher(voucherCode))
                .build());
    }

    @Operation(summary = "Adjust points for a booking", description = "Adjusts loyalty points for a specific booking")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Points adjusted successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid booking ID"),
            @ApiResponse(responseCode = "400", description = "Booking not found")
    })
    @PostMapping("/points/adjust/{bookingId}")
    public ResponseEntity<APIResponse> adjustPoints(
            @Parameter(description = "Booking ID for points adjustment", required = true)
            @PathVariable String bookingId) {
        return ResponseEntity.ok(APIResponse.builder()
                .data(loyaltyService.adjustPoints(bookingId))
                .build());
    }
}
