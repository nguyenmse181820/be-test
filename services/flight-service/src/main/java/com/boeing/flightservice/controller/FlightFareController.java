package com.boeing.flightservice.controller;

import java.util.Map;

import com.boeing.flightservice.service.spec.FlightFareService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.converter.json.MappingJacksonValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.boeing.flightservice.annotation.StandardAPIResponses;
import com.boeing.flightservice.annotation.StandardGetParams;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/flight-fares")
@Tag(name = "Flight Fares", description = "Flight fare management API")
@RequiredArgsConstructor
public class FlightFareController {

    private final FlightFareService service;

    @GetMapping
    @Operation(summary = "Get all flight fares", description = "Get all flight fares with optional filtering, sorting, and pagination")
    @StandardGetParams
    @StandardAPIResponses
    public MappingJacksonValue getAllFlightFares(@RequestParam Map<String, String> params) {
        return service.findAll(params);
    }
}
