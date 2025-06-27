package com.boeing.bookingservice.integration.ls.dto;

import lombok.Data;

@Data
public class LsEarnPointsResponseDTO {
    private String status;
    private Long pointsEarnedThisTransaction;
    private String message;
}