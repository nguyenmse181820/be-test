package com.boeing.bookingservice.dto.response;

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
public class DetailedFareInfoDTO {
    private UUID flightFareId;
    private String name;
    private Double price;
    private Integer seatsAvailableForFare;
    private String baggageAllowance;
    private List<BenefitInfoDTO> benefits;
}