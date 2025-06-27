package com.boeing.bookingservice.integration.fs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FsReleaseSeatsRequestDTO {
    private String bookingReference;
    private List<String> seatCodes;
    private String reason;
}
