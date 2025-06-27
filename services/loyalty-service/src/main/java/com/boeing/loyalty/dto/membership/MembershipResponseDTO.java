package com.boeing.loyalty.dto.membership;

import com.boeing.loyalty.entity.enums.MembershipTier;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MembershipResponseDTO {
    private UUID id;
    private UUID userId;
    private MembershipTier tier;
    private Integer points;
    private Integer totalEarnedPoints;
    private Double totalSpent;
    private List<PointTransactionResponseDTO> transactions;
} 