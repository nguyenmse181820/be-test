package com.boeing.bookingservice.controller;

import com.boeing.bookingservice.dto.response.ApiResponse;
import com.boeing.bookingservice.integration.ls.LoyaltyClient;
import com.boeing.bookingservice.integration.ls.dto.LsUserVoucherDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/loyalty-proxy")
@RequiredArgsConstructor
@Slf4j
public class LoyaltyProxyController {

    private final LoyaltyClient loyaltyClient;

    @GetMapping("/users/{userId}/vouchers")
    public ResponseEntity<ApiResponse<List<LsUserVoucherDTO>>> getActiveUserVouchers(@PathVariable UUID userId) {
        log.info("[LOYALTY_PROXY] Fetching active vouchers for user: {}", userId);
        
        try {
            ApiResponse<List<LsUserVoucherDTO>> response = loyaltyClient.getActiveUserVouchers(userId);
            
            if (response != null && response.getData() != null) {
                log.info("[LOYALTY_PROXY] Successfully fetched {} vouchers for user: {}", 
                        response.getData().size(), userId);
                return ResponseEntity.ok(response);
            } else {
                log.warn("[LOYALTY_PROXY] Loyalty service returned empty or null response for user: {}", userId);
                ApiResponse<List<LsUserVoucherDTO>> emptyResponse = ApiResponse.<List<LsUserVoucherDTO>>builder()
                        .success(true)
                        .message("No vouchers available")
                        .data(Collections.emptyList())
                        .build();
                return ResponseEntity.ok(emptyResponse);
            }
            
        } catch (Exception e) {
            log.error("[LOYALTY_PROXY] Error fetching vouchers for user {}: {}", userId, e.getMessage(), e);
            
            // Return graceful error response instead of throwing exception
            ApiResponse<List<LsUserVoucherDTO>> errorResponse = ApiResponse.<List<LsUserVoucherDTO>>builder()
                    .success(false)
                    .message("Loyalty service is temporarily unavailable. Vouchers cannot be loaded at this time.")
                    .data(Collections.emptyList())
                    .build();
            
            return ResponseEntity.ok(errorResponse); // Return 200 with error info instead of 500
        }
    }
}
