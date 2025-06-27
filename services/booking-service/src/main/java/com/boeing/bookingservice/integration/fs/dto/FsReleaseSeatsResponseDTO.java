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
public class FsReleaseSeatsResponseDTO {
    private String status;
    private List<String> releasedSeats;
    private List<String> failedToReleaseSeats;
    private String message;
}
