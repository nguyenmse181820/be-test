package com.boeing.bookingservice.integration.fs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FsFareOptionBasic {
    private UUID fareId;
    private String fareName;
    private Double minPrice;
    private Integer seatsAvailable;
}