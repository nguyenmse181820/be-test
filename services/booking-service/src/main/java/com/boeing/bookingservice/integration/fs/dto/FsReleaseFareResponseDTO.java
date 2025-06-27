package com.boeing.bookingservice.integration.fs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FsReleaseFareResponseDTO {
    private boolean success;
    private String fareName;
    private int releasedCount;
    private String message;
}
