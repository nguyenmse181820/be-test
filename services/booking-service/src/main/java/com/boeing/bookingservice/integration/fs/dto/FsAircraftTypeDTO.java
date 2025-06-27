package com.boeing.bookingservice.integration.fs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FsAircraftTypeDTO {
    private String manufacturer;
    private String model;
    private String seatMapLayout;
    private Long totalSeats;
}