package com.boeing.loyalty.dto.membership;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
// SERIALIZATION FIX: Added @Getter annotation to enable JSON serialization
// Jackson requires getter methods to serialize fields to JSON response
// Without @Getter, fields remain private and Jackson cannot access them
public class EarnPointsResponseDTO {
    private String status;
    private Long pointsEarnedThisTransaction;
    private String message;
}
