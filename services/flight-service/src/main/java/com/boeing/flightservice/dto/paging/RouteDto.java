package com.boeing.flightservice.dto.paging;

import java.util.UUID;

import com.boeing.flightservice.entity.Route;
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
public class RouteDto {
    UUID id;
    AirportDto origin;
    AirportDto destination;
    Double estimatedDurationMinutes;
    
    public static RouteDto fromEntity(Route route) {
        return RouteDto.builder()
                .id(route.getId())
                .origin(AirportDto.fromEntity(route.getOrigin()))
                .destination(AirportDto.fromEntity(route.getDestination()))
                .estimatedDurationMinutes(route.getEstimatedDurationMinutes())
                .build();
    }
}
