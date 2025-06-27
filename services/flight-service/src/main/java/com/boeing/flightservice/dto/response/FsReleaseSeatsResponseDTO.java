package com.boeing.flightservice.dto.response;

import lombok.Builder;

import java.util.List;

@Builder
public record FsReleaseSeatsResponseDTO(
        String status,
        List<String> releasedSeats,
        List<String> failedToReleaseSeats
) {
}
