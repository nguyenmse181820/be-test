package com.boeing.flightservice.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RouteCreateRequestDTO {

    @NotNull(message = "Origin airport ID is required")
    UUID originAirportId;

    @NotNull(message = "Destination airport ID is required")
    UUID destinationAirportId;

    @NotNull(message = "Estimated duration is required")
    @Positive(message = "Estimated duration must be positive")
    Integer estimatedDurationMinutes;
}
