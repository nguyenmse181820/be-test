package com.boeing.flightservice.dto.union;

import com.boeing.flightservice.dto.response.FsFlightWithFareDetailsDTO;
import lombok.Builder;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Builder
@SuppressWarnings("unused")
public record Search(
) {
    @Builder
    public record Request(
            UUID routeId,
            LocalDate departureDate,
            Integer noAdults,
            Integer noChildren,
            Integer noBabies
    ) {
    }

    @Builder
    public record Response(
            Integer total,
            List<FsFlightWithFareDetailsDTO> directs,
            List<List<FsFlightWithFareDetailsDTO>> connects
    ) {
    }
}
