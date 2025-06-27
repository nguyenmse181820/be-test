package com.boeing.flightservice.dto.paging;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFilter;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonFilter("dynamicFilter")
public class FlightFareDto {
    UUID id;
    Double minPrice;
    Double maxPrice;
    String name;
    FlightDto flight;
    List<BenefitDto> benefits;
}