package com.boeing.flightservice.dto.paging;

import java.time.LocalDateTime;
import java.util.UUID;

import com.boeing.flightservice.entity.enums.FlightStatus;
import com.fasterxml.jackson.annotation.JsonFilter;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

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
    Double flightDurationMinutes;
    FlightStatus status;
}