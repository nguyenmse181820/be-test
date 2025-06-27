package com.boeing.aircraftservice.dtos.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@NoArgsConstructor(force = true)
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AircraftTypeResponseDTO {
    UUID id;
    String model;
    Object seatMap;
    String manufacturer;
    Integer totalSeats;
    boolean deleted;
}
