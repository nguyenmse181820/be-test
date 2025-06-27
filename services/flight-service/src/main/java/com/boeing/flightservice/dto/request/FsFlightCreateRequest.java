package com.boeing.flightservice.dto.request;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record FsFlightCreateRequest(
        String code,
        UUID aircraftId,
        UUID destinationId,
        UUID originId,
        LocalDateTime departureTime,
        List<FlightFareRequest> fares
) {
    @Builder
    public record FlightFareRequest(
            Double minPrice,
            Double maxPrice,
            String name,
            String seatRange,
            List<UUID> benefits
    ) {
    }
}
