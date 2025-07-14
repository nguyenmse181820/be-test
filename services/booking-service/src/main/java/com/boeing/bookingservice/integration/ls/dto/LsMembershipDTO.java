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
public class LsMembershipDTO {
    private UUID id;
    private UUID userId;
    private String tier;
    private Integer points;
    private Integer totalEarnedPoints;
}