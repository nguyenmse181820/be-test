package com.boeing.bookingservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FareOptionSummaryDTO {
    private UUID fareId;
    private String name;
    private Double price;
    private Integer seatsAvailable;
}