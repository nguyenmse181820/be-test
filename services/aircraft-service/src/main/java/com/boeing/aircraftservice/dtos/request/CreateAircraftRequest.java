package com.boeing.aircraftservice.dtos.request;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Value;

@Value
@NoArgsConstructor(force = true)
@AllArgsConstructor
public class CreateAircraftRequest {
    String code;
    CreateAircraftTypeRequest aircraftType;
}
