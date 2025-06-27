package com.boeing.bookingservice.integration.ls.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LsAwardPointsRequestDTO {
    
    private UUID userId;
    private Integer points;
    private String reason;
    private UUID referenceId;
    private String referenceType;
    private String description;
}
