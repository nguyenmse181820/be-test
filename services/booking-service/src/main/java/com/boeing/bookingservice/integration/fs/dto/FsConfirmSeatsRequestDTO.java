package com.boeing.bookingservice.integration.fs.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class FsConfirmSeatsRequestDTO {
    private String bookingReference;
    private List<String> seatCodes;
}
