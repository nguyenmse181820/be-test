package com.boeing.aircraftservice.dtos.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@NoArgsConstructor(force = true)
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AircraftResponseDTO {
    UUID id;
    String code;
    AircraftTypeResponseDTO aircraftType;
    boolean deleted;
}
