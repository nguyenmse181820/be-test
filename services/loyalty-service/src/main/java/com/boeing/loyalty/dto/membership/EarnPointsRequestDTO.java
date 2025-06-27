package com.boeing.loyalty.dto.membership;

import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
public class EarnPointsRequestDTO {
    private UUID userId;
    private String bookingReference;
    private BigDecimal amountSpent;
    private String source;
}
