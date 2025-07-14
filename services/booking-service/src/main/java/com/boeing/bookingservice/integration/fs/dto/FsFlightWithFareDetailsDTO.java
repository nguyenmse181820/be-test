package com.boeing.bookingservice.integration.fs.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
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
    private Integer flightDurationMinutes;
    private int totalSeats;
    private int remainingSeats;
    private List<String> occupiedSeats;
    private List<FsDetailedFareDTO> availableFares;
    private Integer carryOnLuggageWeight;
    private Integer checkedBaggageWeight;
}