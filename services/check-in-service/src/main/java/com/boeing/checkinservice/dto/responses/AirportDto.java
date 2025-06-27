package com.boeing.checkinservice.dto.responses;

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
public class AirportDto {
    UUID id;
    String name;
    String code;
    String city;
    String country;
    String timezone;
}