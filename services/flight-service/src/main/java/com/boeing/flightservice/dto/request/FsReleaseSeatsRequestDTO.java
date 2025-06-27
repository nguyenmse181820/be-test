package com.boeing.flightservice.dto.request;

import lombok.Builder;

import java.util.List;

@Builder
public record FsReleaseSeatsRequestDTO(
        String bookingReference,
        List<String> seatCodes
) {
}
