package com.boeing.bookingservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO containing essential flight details for analysis
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightDetailDTO {
    
    private UUID id;
    private String flightNumber;
    private String departureAirportCode;
    private String arrivalAirportCode;
    private LocalDateTime departureTime;
    private LocalDateTime arrivalTime;
    private String aircraftType;
    private Integer duration; // in minutes
}
