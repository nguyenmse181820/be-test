package com.boeing.flightservice.controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJacksonValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.boeing.flightservice.annotation.StandardAPIResponses;
import com.boeing.flightservice.annotation.StandardGetParams;
import com.boeing.flightservice.dto.request.RouteCreateRequestDTO;
import com.boeing.flightservice.dto.response.RouteResponseDTO;
import com.boeing.flightservice.service.spec.RouteService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "3. Route", description = "APIs for managing flight routes")
@RestController
@RequiredArgsConstructor
public class RouteController {
    
    private final RouteService routeService;

    @GetMapping("/api/v1/fs/routes")
    @Operation(summary = "Get all routes", description = "Get all routes with optional filtering, sorting, and pagination")
    @StandardGetParams
    @StandardAPIResponses
    public MappingJacksonValue getAllRoutes(@RequestParam Map<String, String> params) {
        return routeService.findAll(params);
    }

    @PostMapping("/api/v1/fs/routes")
    @Operation(summary = "Create a new route", description = "Create a new flight route between two airports")
    @StandardAPIResponses
    public ResponseEntity<RouteResponseDTO> createRoute(@Valid @RequestBody RouteCreateRequestDTO request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(routeService.createRoute(request));
    }

    @GetMapping("/api/v1/fs/routes/{id}")
    @Operation(summary = "Get route by ID", description = "Get a specific route by its ID")
    @StandardAPIResponses
    public ResponseEntity<RouteResponseDTO> getRouteById(@PathVariable UUID id) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(routeService.getRouteById(id));
    }

    @GetMapping("/api/v1/fs/routes/duration")
    @Operation(summary = "Get estimated duration between airports", description = "Get the estimated flight duration between two airports")
    @StandardAPIResponses
    public ResponseEntity<?> getEstimatedDuration(
            @RequestParam UUID originAirportId,
            @RequestParam UUID destinationAirportId) {
        Integer duration = routeService.getEstimatedDuration(originAirportId, destinationAirportId);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(duration);
    }
}
