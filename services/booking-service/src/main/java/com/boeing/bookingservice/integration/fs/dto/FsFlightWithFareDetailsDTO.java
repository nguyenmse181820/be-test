package com.boeing.bookingservice.integration.fs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FsFlightWithFareDetailsDTO {
    private UUID flightId;
    private String flightCode;
    private FsAircraftDTO aircraft;
    private FsAirportSummaryDTO originAirport;
    private FsAirportSummaryDTO destinationAirport;
    private LocalDateTime departureTime;
    private LocalDateTime estimatedArrivalTime;
    private LocalDateTime actualArrivalTime;
    private String status;
    private String flightDuration;
    private Map<String, String> seatAvailabilityMap;
    private List<FsDetailedFareDTO> availableFares;
}