package com.boeing.flightservice.dto.union;

import com.boeing.flightservice.entity.Airport;
import lombok.Builder;

import java.util.UUID;

@Builder
@SuppressWarnings("unused")
public record AirportDTO() {

    @Builder
    public record CreateRequest(
            String name,
            String code,
            String city,
            String country,
            String timezone,
            Double latitude,
            Double longitude
    ) {
    }

    @Builder
    public record UpdateRequest(
            String name,
            String code,
            String city,
            String country,
            String timezone,
            Double latitude,
            Double longitude
    ) {
    }

    @Builder
    public record Response(
            UUID id,
            String name,
            String code,
            String city,
            String country,
            String timezone,
            Double latitude,
            Double longitude
    ) {
    }

    public static Response fromEntity(Airport entity) {
        return Response.builder()
                .id(entity.getId())
                .name(entity.getName())
                .code(entity.getCode())
                .city(entity.getCity())
                .country(entity.getCountry())
                .timezone(entity.getTimezone())
                .latitude(entity.getLatitude())
                .longitude(entity.getLongitude())
                .build();
    }
}
