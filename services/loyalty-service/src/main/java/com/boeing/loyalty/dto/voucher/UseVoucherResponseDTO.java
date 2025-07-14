package com.boeing.loyalty.dto.voucher;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;

@Builder
@Getter
// SERIALIZATION FIX: Added @Getter annotation to enable JSON serialization
// Jackson requires getter methods to serialize fields to JSON response
public class UseVoucherResponseDTO {
    private boolean success;
    private String status;
    private String message;
    private String errorMessage;
    private String voucherCode;
    private LocalDateTime usedAt;
    private Double discountAmount;
}
