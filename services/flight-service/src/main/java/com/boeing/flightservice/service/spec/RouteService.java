package com.boeing.flightservice.service.spec;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.converter.json.MappingJacksonValue;

import com.boeing.flightservice.dto.request.RouteCreateRequestDTO;
import com.boeing.flightservice.dto.response.RouteResponseDTO;

public interface RouteService {
    MappingJacksonValue findAll(Map<String, String> params);
    RouteResponseDTO createRoute(RouteCreateRequestDTO request);
    RouteResponseDTO getRouteById(UUID id);
    Integer getEstimatedDuration(UUID originAirportId, UUID destinationAirportId);
}
