package com.boeing.flightservice.controller;

import com.boeing.flightservice.annotation.StandardAPIResponses;
import com.boeing.flightservice.annotation.StandardGetParams;
import com.boeing.flightservice.service.spec.AirportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.converter.json.MappingJacksonValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "1. Airport", description = "APIs for managing airports")
@RestController
@RequestMapping("/api/v1/airports")
@RequiredArgsConstructor
public class AirportController {

    private final AirportService airportService;

    @Operation(summary = "Get all airports with optional pagination and filtering")
    @GetMapping
    @StandardGetParams
    @StandardAPIResponses
    public MappingJacksonValue getAllAirports(@Parameter(hidden = true) @RequestParam Map<String, String> params) {
        return airportService.findAll(params);
    }
}
