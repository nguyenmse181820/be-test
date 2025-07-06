package com.boeing.bookingservice.integration.fs.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class FsDetailedFareDTO {
    @JsonProperty("id")
    private UUID flightFareId;
    private String name;
    private Double price;
    
    @JsonProperty("remainingSeats")
    private Integer seatsAvailableForFare;
    
    private String seatRange;
    private Integer totalSeats;
    private String baggageAllowance;
    private FsFareRuleDTO fareRules;
    private List<UUID> benefits;
    private List<String> conditions;
}