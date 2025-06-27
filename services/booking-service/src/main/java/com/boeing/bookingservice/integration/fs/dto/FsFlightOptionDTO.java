package com.boeing.bookingservice.integration.fs.dto;

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
public class FsFlightOptionDTO {
    private UUID flightId;
    private String flightCode;
    private FsAirportSummaryDTO originAirport;
    private FsAirportSummaryDTO destinationAirport;
    private LocalDateTime departureTime;
    private LocalDateTime arrivalTime;
    private String flightDuration;
    private String aircraftTypeModel;
    private List<FsFareOptionBasic> availableFares;
}