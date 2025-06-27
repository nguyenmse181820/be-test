package com.boeing.flightservice.dto.response;

import java.util.UUID;

import com.boeing.flightservice.entity.Route;

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
public class RouteResponseDTO {
    UUID id;
    AirportResponseDTO origin;
    AirportResponseDTO destination;
    Double estimatedDurationMinutes;
    
    public static RouteResponseDTO fromEntity(Route route) {
        return RouteResponseDTO.builder()
                .id(route.getId())
                .origin(AirportResponseDTO.fromEntity(route.getOrigin()))
                .destination(AirportResponseDTO.fromEntity(route.getDestination()))
                .estimatedDurationMinutes(route.getEstimatedDurationMinutes())
                .build();
    }
}
