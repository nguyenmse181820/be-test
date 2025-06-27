package com.boeing.bookingservice.integration.ls.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LsAdjustPointsRequestDTO {

    @NotNull(message = "User ID cannot be null")
    private UUID userId;

    @NotBlank(message = "Booking reference cannot be blank if adjustment is related to a booking")
    private String bookingReference;

    @NotNull(message = "Points to adjust cannot be null")
    private Long pointsToAdjust;

    @NotBlank(message = "Reason for adjustment cannot be blank")
    private String reason;

    private String notes;

    private UUID initiatedBy;
}