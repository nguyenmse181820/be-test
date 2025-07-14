package com.boeing.bookingservice.integration.ls;

import com.boeing.bookingservice.dto.response.ApiResponse;
import com.boeing.bookingservice.integration.ls.dto.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class LoyaltyClientWrapper {
    
    private final LoyaltyClient loyaltyClient;
    private final LoyaltyClientFallback fallback;

    @CircuitBreaker(name = "loyaltyServiceCircuitBreaker", fallbackMethod = "earnPointsFallback")
    @Retry(name = "loyaltyServiceRetry")
    public ApiResponse<LsEarnPointsResponseDTO> earnPoints(LsEarnPointsRequestDTO request) {
        return loyaltyClient.earnPoints(request);
    }

    @CircuitBreaker(name = "loyaltyServiceCircuitBreaker", fallbackMethod = "getActiveUserVouchersFallback")
    @Retry(name = "loyaltyServiceRetry")
    public ApiResponse<List<LsUserVoucherDTO>> getActiveUserVouchers(UUID userId) {
        return loyaltyClient.getActiveUserVouchers(userId);
    }

    @CircuitBreaker(name = "loyaltyServiceCircuitBreaker", fallbackMethod = "getMembershipFallback")
    @Retry(name = "loyaltyServiceRetry")
    public ApiResponse<LsMembershipDTO> getMembership(UUID userId) {
        return loyaltyClient.getMembership(userId);
    }

    // Delegate other methods without circuit breaker for now
    public ApiResponse<LsUseVoucherResponseDTO> useVoucher(String voucherCode) {
        return loyaltyClient.useVoucher(voucherCode);
    }

    public ApiResponse<LsUserVoucherDTO> getUserVoucherById(UUID id) {
        return loyaltyClient.getUserVoucherById(id);
    }

    public ApiResponse<LsUserVoucherDTO> redeemVoucherForUser(UUID userId, UUID templateId) {
        return loyaltyClient.redeemVoucherForUser(userId, templateId);
    }

    public ApiResponse<LsUserVoucherDTO> useUserVoucherById(UUID id) {
        return loyaltyClient.useUserVoucherById(id);
    }

    public ApiResponse<LsValidateVoucherResponseDTO> validateVoucher(LsValidateVoucherRequestDTO request) {
        return loyaltyClient.validateVoucher(request);
    }

    public ApiResponse<String> adjustPoints(String bookingId) {
        return loyaltyClient.adjustPoints(bookingId);
    }

    // Fallback methods
    public ApiResponse<LsEarnPointsResponseDTO> earnPointsFallback(LsEarnPointsRequestDTO request, Exception ex) {
        return fallback.earnPointsFallback(request, ex);
    }

    public ApiResponse<List<LsUserVoucherDTO>> getActiveUserVouchersFallback(UUID userId, Exception ex) {
        return fallback.getActiveUserVouchersFallback(userId, ex);
    }

    public ApiResponse<LsMembershipDTO> getMembershipFallback(UUID userId, Exception ex) {
        return fallback.getMembershipFallback(userId, ex);
    }
}