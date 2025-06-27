package com.boeing.flightservice.dto.response;

import com.boeing.flightservice.entity.Airport;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AirportResponseDTO {
    UUID id;
    String name;
    String code;
    String city;
    String country;
    String timezone;

    public static AirportResponseDTO fromEntity(Airport airport) {
        return AirportResponseDTO.builder()
                .id(airport.getId())
                .name(airport.getName())
                .code(airport.getCode())
                .city(airport.getCity())
                .country(airport.getCountry())
                .timezone(airport.getTimezone())
                .build();
    }
}