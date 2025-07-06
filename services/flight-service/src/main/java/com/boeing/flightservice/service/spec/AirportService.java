package com.boeing.flightservice.service.spec;

import com.boeing.flightservice.dto.union.AirportDTO;
import org.springframework.http.converter.json.MappingJacksonValue;

import java.util.Map;
import java.util.UUID;

public interface AirportService {
    MappingJacksonValue findAll(Map<String, String> params);

    AirportDTO.Response createAirport(AirportDTO.CreateRequest request);

    AirportDTO.Response updateAirport(UUID airportId, AirportDTO.UpdateRequest request);

    void deleteAirport(UUID id);
}