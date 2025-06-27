package com.boeing.bookingservice.dto.response;

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
public class FlightWithFareDetailsDTO {
    private UUID flightId;
    private String flightCode;
    private AircraftInfoDTO aircraft;
    private AirportSummaryDTO originAirport;
    private AirportSummaryDTO destinationAirport;
    private LocalDateTime departureTime;
    private LocalDateTime estimatedArrivalTime;
    private LocalDateTime actualArrivalTime;
    private String status;
    private String duration;
    private Map<String, String> seatMapWithStatus;
    private List<DetailedFareInfoDTO> availableFares;
}