package com.boeing.bookingservice.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO to hold seat selection data for a single passenger across multiple flights
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MultiSegmentSeatDTO {
    private int passengerIndex;
    private int flightIndex;
    private String seatCode;
}
