package com.boeing.loyalty.dto.voucher;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidateVoucherResponseDTO {
    private boolean valid;
    private Double discountAmount;
    private Double maxDiscount;
    private Double minSpend;
    private String errorMessage;
    private String voucherCode;
    private Integer discountPercentage;
}
