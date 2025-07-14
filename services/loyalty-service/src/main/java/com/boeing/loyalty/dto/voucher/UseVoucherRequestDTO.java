package com.boeing.loyalty.dto.voucher;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UseVoucherRequestDTO {
    
    @NotBlank(message = "Voucher code is required")
    private String voucherCode;
    
    @NotNull(message = "User ID is required")
    private UUID userId;
    
    @NotBlank(message = "Booking reference is required")
    private String bookingReference;
}