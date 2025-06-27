package com.boeing.bookingservice.integration.ls;

import com.boeing.bookingservice.dto.response.ApiResponse;
import com.boeing.bookingservice.integration.ls.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "loyalty-service", url = "${services.loyalty-service.url}", path = "/api/v1")
public interface LoyaltyClient {

    @GetMapping("/loyalty/{userId}/vouchers")
    ApiResponse<List<LsUserVoucherDTO>> getActiveUserVouchers(@PathVariable("userId") UUID userId);

    @PostMapping("/loyalty/points/earn")
    ApiResponse<LsEarnPointsResponseDTO> earnPoints(@RequestBody LsEarnPointsRequestDTO request);

    @PostMapping("/loyalty/vouchers/{voucherCode}/use")
    ApiResponse<LsUseVoucherResponseDTO> useVoucher(@PathVariable("voucherCode") String voucherCode);

    @PostMapping("/loyalty/points/adjust/{bookingId}")
    ApiResponse<LsAdjustPointsResponseDTO> adjustPointsForCancelledBooking(@PathVariable("bookingId") String bookingId);

    @GetMapping("/user-vouchers/{id}")
    ApiResponse<LsUserVoucherDTO> getUserVoucherById(@PathVariable("id") UUID id);

    @PostMapping("/user-vouchers/user/{userId}/redeem/{templateId}")
    ApiResponse<LsUserVoucherDTO> redeemVoucherForUser(
            @PathVariable("userId") UUID userId,
            @PathVariable("templateId") UUID templateId);

    @PostMapping("/user-vouchers/{id}/use")
    ApiResponse<LsUserVoucherDTO> useUserVoucherById(@PathVariable("id") UUID id);
}