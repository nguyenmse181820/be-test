package com.boeing.flightservice.dto.response;

import com.boeing.flightservice.entity.enums.FlightStatus;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record FlightResponseDTO(
        UUID id,
        String code,
        String origin,
        String destination,
        LocalDateTime departureTime,
        LocalDateTime estimatedArrivalTime,
        Integer flightDurationMinutes,
        FlightStatus status
) {
}
