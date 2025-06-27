package com.boeing.bookingservice.integration.fs.dto;

import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FsConfirmFareSaleRequestDTO {
    @Min(1)
    private int soldCount;
    private String bookingReference;
}