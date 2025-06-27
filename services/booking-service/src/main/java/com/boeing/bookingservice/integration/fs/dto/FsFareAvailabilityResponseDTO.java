package com.boeing.bookingservice.integration.fs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FsFareAvailabilityResponseDTO {

    private UUID flightId;

    private String fareIdentifier;

    private boolean isAvailableForRequestedCount;

    private Integer requestedCount;

    private Integer actualAvailableCount;

    private Double pricePerPassenger;

    private String currency;

    private String message;

}