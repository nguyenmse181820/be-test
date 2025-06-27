package com.boeing.bookingservice.integration.ls.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LsUserVoucherDTO {
    private String code;
    private String name;
    private Integer discountPercentage;
    private Double minimumPurchaseAmount;
    private Double maximumDiscountAmount;
}