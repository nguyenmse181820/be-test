package com.boeing.bookingservice.integration.fs.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class FsDetailedFareDTO {
    private UUID id;
    private String name;
    private Double price;
    private String fareType;
    private List<String> seats;
    private List<String> occupiedSeats;
    private int totalSeats;
    private List<FsBenefitDTO> benefits;
}