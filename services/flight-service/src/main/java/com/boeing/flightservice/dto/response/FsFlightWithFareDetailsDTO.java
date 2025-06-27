package com.boeing.flightservice.dto.response;

import com.boeing.flightservice.entity.enums.FlightStatus;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record FsFlightWithFareDetailsDTO(
        UUID flightId,
        String flightCode,
        FsAircraftDTO aircraft,
        AirportResponseDTO originAirport,
        AirportResponseDTO destinationAirport,
        LocalDateTime departureTime,
        LocalDateTime estimatedArrivalTime,
        LocalDateTime actualArrivalTime, // Khong biet duong tinh nhu the nao
        FlightStatus status,
        Double flightDurationMinutes,
        int totalSeats,
        int remainingSeats,
        List<String> occupiedSeats,
        List<FsDetailedFareDTO> availableFares
) {
    @Builder
    public record FsAircraftDTO(
            UUID id,
            String code,
            String model
    ) {
    }

    @Builder
    public record FsDetailedFareDTO(
            UUID id,
            Double price,
            String name,
            String seatRange,
            int totalSeats,
            int remainingSeats,
            List<UUID> benefits
    ) {
    }
}
