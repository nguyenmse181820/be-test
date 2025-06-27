package com.boeing.bookingservice.dto.response;

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
public class FlightOptionDTO {
    private UUID flightId;
    private String flightCode;
    private AirportSummaryDTO originAirport;
    private AirportSummaryDTO destinationAirport;
    private LocalDateTime departureTime;
    private LocalDateTime arrivalTime;
    private String duration;
    private String aircraftType;
    private List<FareOptionSummaryDTO> fareOptions;
}