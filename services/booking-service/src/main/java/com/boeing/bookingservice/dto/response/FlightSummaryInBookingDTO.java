package com.boeing.bookingservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightSummaryInBookingDTO {
    private String flightCode;
    private String originAirportCode;
    private String destinationAirportCode;
    private LocalDateTime departureTime;
}