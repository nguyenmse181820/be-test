package com.boeing.flightservice.service.spec;

import org.springframework.http.converter.json.MappingJacksonValue;

import java.util.Map;

public interface FlightFareService {
    MappingJacksonValue findAll(Map<String, String> params);
}
