package com.boeing.loyalty.dto.membership;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.boeing.loyalty.entity.enums.MembershipTier;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private LocalDateTime createdAt; // Join date
    private List<PointTransactionResponseDTO> transactions;
} 