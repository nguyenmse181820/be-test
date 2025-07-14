package com.boeing.flightservice.dto.request;

import com.boeing.flightservice.entity.enums.FareType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record FsFlightCreateRequest(
        @NotBlank(message = "Flight code is required")
        String code,
        
        @NotNull(message = "Aircraft ID is required")
        UUID aircraftId,
        
        @NotNull(message = "Route ID is required")
        UUID routeId,
        
        @NotNull(message = "Departure time is required")
        LocalDateTime departureTime,
        
        @NotNull(message = "Seat class fares are required")
        @NotEmpty(message = "At least one seat class fare must be provided")
        @Valid
        List<SeatClassFareRequest> seatClassFares
) {
    @Builder
    public record SeatClassFareRequest(
            @NotNull(message = "Fare type is required")
            FareType fareType,
            
            @NotNull(message = "Minimum price is required")
            @Positive(message = "Minimum price must be positive")
            Double minPrice,
            
            @NotNull(message = "Maximum price is required")
            @Positive(message = "Maximum price must be positive")
            Double maxPrice,
            
            @NotBlank(message = "Fare name is required")
            String name,
            
            List<UUID> benefits
    ) {
    }
}
