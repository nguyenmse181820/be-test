package com.boeing.bookingservice.integration.ls;

import com.boeing.bookingservice.dto.response.ApiResponse;
import com.boeing.bookingservice.integration.ls.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class LoyaltyClientFallback {

    public ApiResponse<LsEarnPointsResponseDTO> earnPointsFallback(LsEarnPointsRequestDTO request, Exception ex) {
        log.error("Loyalty service circuit breaker activated for earnPoints. Booking: {}, Error: {}", 
                request.getBookingReference(), ex.getMessage());
        
        return ApiResponse.<LsEarnPointsResponseDTO>builder()
                .success(false)
                .message("Loyalty service temporarily unavailable. Points will be awarded later.")
                .data(LsEarnPointsResponseDTO.builder()
                        .status("DEFERRED")
                        .pointsEarnedThisTransaction(0L)
                        .message("Points award deferred due to service unavailability")
                        .build())
                .build();
    }

    public ApiResponse<List<LsUserVoucherDTO>> getActiveUserVouchersFallback(UUID userId, Exception ex) {
        log.warn("Loyalty service circuit breaker activated for getActiveUserVouchers. User: {}, Error: {}", 
                userId, ex.getMessage());
        
        return ApiResponse.<List<LsUserVoucherDTO>>builder()
                .success(true)
                .message("Loyalty service temporarily unavailable")
                .data(Collections.emptyList())
                .build();
    }

    public ApiResponse<LsMembershipDTO> getMembershipFallback(UUID userId, Exception ex) {
        log.warn("Loyalty service circuit breaker activated for getMembership. User: {}, Error: {}", 
                userId, ex.getMessage());
        
        return ApiResponse.<LsMembershipDTO>builder()
                .success(false)
                .message("Loyalty service temporarily unavailable")
                .data(null)
                .build();
    }
}