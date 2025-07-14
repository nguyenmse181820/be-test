package com.boeing.loyalty.dto.voucher;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidateVoucherRequestDTO {
    private String voucherCode;
    private UUID userId;
    private Double bookingAmount;
    private String flightCode;
}
