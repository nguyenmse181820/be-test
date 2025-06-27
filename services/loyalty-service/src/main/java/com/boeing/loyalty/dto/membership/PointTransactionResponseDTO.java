package com.boeing.loyalty.dto.membership;

import com.boeing.loyalty.entity.enums.PointType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PointTransactionResponseDTO {
    private UUID id;
    private PointType type;
    private String source;
    private Integer points;
    private String note;
    private UUID membershipId;
    private LocalDateTime createdAt;
} 