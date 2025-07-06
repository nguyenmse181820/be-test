package com.boeing.flightservice.dto.paging;

import com.boeing.flightservice.entity.Route;
import com.fasterxml.jackson.annotation.JsonFilter;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

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
    Integer estimatedDurationMinutes;

    public static RouteDto fromEntity(Route route) {
        return RouteDto.builder()
                .id(route.getId())
                .origin(AirportDto.fromEntity(route.getOrigin()))
                .destination(AirportDto.fromEntity(route.getDestination()))
                .estimatedDurationMinutes(route.getEstimatedDurationMinutes())
                .build();
    }
}
