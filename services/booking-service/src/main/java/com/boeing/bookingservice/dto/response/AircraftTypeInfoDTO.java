package com.boeing.bookingservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AircraftTypeInfoDTO {
    private String manufacturer;
    private String model;
    private List<SeatMapSeatDTO> parsedSeatMap;
    private Long totalSeats;
}