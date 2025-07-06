package com.boeing.flightservice.dto.response;

import com.boeing.flightservice.entity.Route;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RouteResponseDTO {
    UUID id;
    AirportResponseDTO origin;
    AirportResponseDTO destination;
    Integer estimatedDurationMinutes;

    public static RouteResponseDTO fromEntity(Route route) {
        return RouteResponseDTO.builder()
                .id(route.getId())
                .origin(AirportResponseDTO.fromEntity(route.getOrigin()))
                .destination(AirportResponseDTO.fromEntity(route.getDestination()))
                .estimatedDurationMinutes(route.getEstimatedDurationMinutes())
                .build();
    }
}
