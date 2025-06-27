package com.boeing.checkinservice.dto.responses;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Builder
public record FsFlightWithFareDetailsDTO(
        UUID flightId,
        String flightCode,
        FsAircraftDTO aircraft,
        AirportResponseDto originAirport,
        AirportResponseDto destinationAirport,
        LocalDateTime departureTime,
        LocalDateTime estimatedArrivalTime,
        LocalDateTime actualArrivalTime,
        String status,
        String flightDuration,
        Map<String, String> seatAvailabilityMap,
        List<FsDetailedFareDTO> availableFares
) {
    @Builder
    public record FsAircraftDTO(
            UUID id,
            String name
    ) {
    }

    @Builder
    public record FsDetailedFareDTO(
            UUID id,
            Double minPrice,
            Double maxPrice,
            String name,
            FlightDto flight
    ) {
    }
}
