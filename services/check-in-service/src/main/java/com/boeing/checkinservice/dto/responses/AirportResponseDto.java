package com.boeing.checkinservice.dto.responses;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AirportResponseDto {
    UUID id;
    String name;
    String code;
    String city;
    String country;
    String timezone;
}