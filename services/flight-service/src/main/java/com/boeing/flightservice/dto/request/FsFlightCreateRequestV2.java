package com.boeing.flightservice.dto.request;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import lombok.Builder;

@Builder
public record FsFlightCreateRequestV2(
        String code,
        UUID aircraftId,
        UUID destinationId,
        UUID originId,
        LocalDateTime departureTime,
        List<SeatClassFareRequest> seatClassFares
) {
    @Builder
    public record SeatClassFareRequest(
            String seatClassName, // e.g., "economy", "business", "first"
            Double minPrice,
            Double maxPrice,
            String name, // Custom fare name for this seat class
            List<UUID> benefits
    ) {
    }
}
