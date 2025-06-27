package com.boeing.loyalty.dto.membership;

import lombok.Builder;

@Builder
public class EarnPointsResponseDTO {
    private String status;
    private Long pointsEarnedThisTransaction;
    private String message;
}
