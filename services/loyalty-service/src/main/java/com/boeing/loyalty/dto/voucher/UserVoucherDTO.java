package com.boeing.loyalty.dto.voucher;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserVoucherDTO {
    String code;
    String name;
    Integer discountPercentage;
    Double minimumPurchaseAmount;
    Double maximumDiscountAmount;
}
