package com.boeing.flightservice.dto.paging;

import java.util.UUID;

import com.boeing.flightservice.entity.Airport;
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
public class AirportDto {
    UUID id;
    String name;
    String code;
    String city;
    String country;
    String timezone;
    
    public static AirportDto fromEntity(Airport airport) {
        return AirportDto.builder()
                .id(airport.getId())
                .name(airport.getName())
                .code(airport.getCode())
                .city(airport.getCity())
                .country(airport.getCountry())
                .timezone(airport.getTimezone())
                .build();
    }
}