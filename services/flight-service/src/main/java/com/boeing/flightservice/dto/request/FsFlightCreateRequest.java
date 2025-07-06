package com.boeing.flightservice.dto.request;

import com.boeing.flightservice.entity.enums.FareType;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record FsFlightCreateRequest(
        String code,
        UUID aircraftId,
        UUID routeId,
        LocalDateTime departureTime,
        List<SeatClassFareRequest> seatClassFares
) {
    @Builder
    public record SeatClassFareRequest(
            FareType fareType,
            Double minPrice,
            Double maxPrice,
            String name, // Custom fare name for this seat class
            List<UUID> benefits
    ) {
    }
}
