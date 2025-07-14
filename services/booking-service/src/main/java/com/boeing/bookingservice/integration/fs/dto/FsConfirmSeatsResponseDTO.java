package com.boeing.bookingservice.integration.fs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FsConfirmSeatsResponseDTO {
    private String status;
    private List<String> confirmedSeats;
    private List<String> failedToConfirmSeats;
    private String message;
}
