package com.boeing.flightservice.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record FsFlightOptionDTO(
         UUID flightId,
         String flightCode,
         AirportResponseDTO originAirport,
         AirportResponseDTO destinationAirport,
         LocalDateTime departureTime,
         LocalDateTime arrivalTime,
         String flightDuration,
         String aircraftTypeModel,
         List<FsFareOptionBasic> availableFares
) {
    public record FsFareOptionBasic(
            UUID fareId,
            String fareName,
            BigDecimal minPrice,
            Integer seatsAvailable
    ) {}
}