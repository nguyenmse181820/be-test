package com.boeing.flightservice.service.impl;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.http.converter.json.MappingJacksonValue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.boeing.flightservice.dto.paging.RouteDto;
import com.boeing.flightservice.dto.request.RouteCreateRequestDTO;
import com.boeing.flightservice.dto.response.RouteResponseDTO;
import com.boeing.flightservice.entity.Airport;
import com.boeing.flightservice.entity.Route;
import com.boeing.flightservice.exception.BadRequestException;
import com.boeing.flightservice.exception.ResourceNotFoundException;
import com.boeing.flightservice.repository.AirportRepository;
import com.boeing.flightservice.repository.RouteRepository;
import com.boeing.flightservice.service.spec.RouteService;
import com.boeing.flightservice.util.PaginationUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class RouteServiceImpl implements RouteService {
    
    private final RouteRepository routeRepository;
    private final AirportRepository airportRepository;

    @Override
    public MappingJacksonValue findAll(Map<String, String> params) {
        return PaginationUtil.findAll(
                params,
                routeRepository,
                RouteDto.class
        );
    }

    @Override
    public RouteResponseDTO createRoute(RouteCreateRequestDTO request) {
        // Validate that origin and destination are different
        if (Objects.equals(request.getOriginAirportId(), request.getDestinationAirportId())) {
            throw new BadRequestException("Origin and destination airports cannot be the same");
        }

        // Find airports
        Airport origin = airportRepository.findByIdAndDeleted(request.getOriginAirportId(), false)
                .orElseThrow(() -> new ResourceNotFoundException("Origin airport not found"));
        
        Airport destination = airportRepository.findByIdAndDeleted(request.getDestinationAirportId(), false)
                .orElseThrow(() -> new ResourceNotFoundException("Destination airport not found"));

        // Check if route already exists
        if (routeRepository.findByOriginAndDestination(request.getOriginAirportId(), request.getDestinationAirportId()).isPresent()) {
            throw new BadRequestException("Route between these airports already exists");
        }

        // Create and save route
        Route route = Route.builder()
                .origin(origin)
                .destination(destination)
                .estimatedDurationMinutes(request.getEstimatedDurationMinutes())
                .build();        route = routeRepository.save(route);
        
        return RouteResponseDTO.fromEntity(route);
    }

    @Override
    public RouteResponseDTO getRouteById(UUID id) {        Route route = routeRepository.findByIdAndDeleted(id, false)
                .orElseThrow(() -> new ResourceNotFoundException("Route not found"));
        
        return RouteResponseDTO.fromEntity(route);
    }

    @Override
    public Double getEstimatedDuration(UUID originAirportId, UUID destinationAirportId) {
        return routeRepository.findByOriginAndDestination(originAirportId, destinationAirportId)
                .map(Route::getEstimatedDurationMinutes)
                .orElse(null);
    }
}
