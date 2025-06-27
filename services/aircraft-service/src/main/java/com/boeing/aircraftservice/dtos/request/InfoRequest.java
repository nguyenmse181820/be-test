package com.boeing.aircraftservice.dtos.request;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Value;

@Value
@NoArgsConstructor(force = true)
@AllArgsConstructor
public class InfoRequest {
    String firstName;
    String lastName;
    int accountID;
}
