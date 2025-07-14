package com.boeing.bookingservice.integration.ls.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LsValidateVoucherResponseDTO {
    private boolean valid;
    private Double discountAmount;
    private Double maxDiscount;
    private Double minSpend;
    private String errorMessage;
}