package com.boeing.checkinservice.dto.responses;

import com.boeing.checkinservice.entity.enums.FlightStatus;
import com.fasterxml.jackson.annotation.JsonFilter;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonFilter("dynamicFilter")
public class FlightDto {
    UUID id;
    String code;
    UUID aircraftId;
    AirportDto destination;
    AirportDto origin;
    LocalDateTime departureTime;
    LocalDateTime estimatedArrivalTime;
    FlightStatus status;
}