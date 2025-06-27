package com.boeing.bookingservice.integration.fs.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class FsConfirmSeatsResponseDTO {
    private String status;
    private List<String> confirmedSeats;
    private List<String> failedToConfirmSeats;
    private String message;
}
