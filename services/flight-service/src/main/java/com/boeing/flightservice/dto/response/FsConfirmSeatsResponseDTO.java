package com.boeing.flightservice.dto.response;

import lombok.Builder;

import java.util.List;

@Builder
public record FsConfirmSeatsResponseDTO(
        String status,
        List<String> confirmedSeats,
        List<String> failedToConfirmSeats,
        String message
) {
}