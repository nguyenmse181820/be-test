package com.boeing.bookingservice.integration.ls.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LsAdjustPointsResponseDTO {
    @NotBlank(message = "Status cannot be blank")
    private String status;
    private String message;
    private UUID membershipId;
    private String currentTier;
    private UUID transactionId;
}