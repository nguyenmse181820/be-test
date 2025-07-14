package com.boeing.bookingservice.integration.ls;

import java.util.List;
import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.boeing.bookingservice.config.FeignConfig;
import com.boeing.bookingservice.dto.response.ApiResponse;
import com.boeing.bookingservice.integration.ls.dto.LsEarnPointsRequestDTO;
import com.boeing.bookingservice.integration.ls.dto.LsEarnPointsResponseDTO;
import com.boeing.bookingservice.integration.ls.dto.LsMembershipDTO;
import com.boeing.bookingservice.integration.ls.dto.LsUseVoucherRequestDTO;
import com.boeing.bookingservice.integration.ls.dto.LsUseVoucherResponseDTO;
import com.boeing.bookingservice.integration.ls.dto.LsUserVoucherDTO;
import com.boeing.bookingservice.integration.ls.dto.LsValidateVoucherRequestDTO;
import com.boeing.bookingservice.integration.ls.dto.LsValidateVoucherResponseDTO;

@FeignClient(name = "loyalty-service", path = "/loyalty-service/api/v1", configuration = FeignConfig.class)
public interface LoyaltyClient {

    @GetMapping("/loyalty/{userId}/vouchers")
    ApiResponse<List<LsUserVoucherDTO>> getActiveUserVouchers(@PathVariable("userId") UUID userId);

    @PostMapping("/loyalty/points/earn")
    ApiResponse<LsEarnPointsResponseDTO> earnPoints(@RequestBody LsEarnPointsRequestDTO request);

    @PostMapping("/loyalty/vouchers/{voucherCode}/use")
    ApiResponse<LsUseVoucherResponseDTO> useVoucher(@PathVariable("voucherCode") String voucherCode);

    @GetMapping("/user-vouchers/{id}")
    ApiResponse<LsUserVoucherDTO> getUserVoucherById(@PathVariable("id") UUID id);

    @PostMapping("/user-vouchers/user/{userId}/redeem/{templateId}")
    ApiResponse<LsUserVoucherDTO> redeemVoucherForUser(
            @PathVariable("userId") UUID userId,
            @PathVariable("templateId") UUID templateId);

    @PostMapping("/user-vouchers/{id}/use")
    ApiResponse<LsUserVoucherDTO> useUserVoucherById(@PathVariable("id") UUID id);

    // New methods for enhanced integration
    @PostMapping("/loyalty/vouchers/validate")
    ApiResponse<LsValidateVoucherResponseDTO> validateVoucher(@RequestBody LsValidateVoucherRequestDTO request);

    @GetMapping("/memberships/user/{userId}")
    ApiResponse<LsMembershipDTO> getMembership(@PathVariable("userId") UUID userId);

    @PostMapping("/loyalty/points/adjust/{bookingId}")
    ApiResponse<String> adjustPoints(@PathVariable("bookingId") String bookingId);

    @PostMapping("/loyalty/vouchers/use")
    LsUseVoucherResponseDTO useVoucher(@RequestBody LsUseVoucherRequestDTO request);

    @PostMapping("/loyalty/vouchers/cancel")
    ApiResponse<String> cancelVoucherUsage(@RequestParam("voucherCode") String voucherCode, 
                                         @RequestParam("userId") UUID userId);
}