package com.boeing.bookingservice.controller;

import com.boeing.bookingservice.integration.fs.FlightClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/flights")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Flight Proxy", description = "Proxy endpoints to flight service for updated data")
public class FlightProxyController {

    private final FlightClient flightClient;

    @GetMapping("/{flightId}/fresh-details")
    @Operation(summary = "Get fresh flight details", 
               description = "Get up-to-date flight details directly from flight service")
    public ResponseEntity<?> getFreshFlightDetails(@PathVariable UUID flightId) {
        try {
            log.info("Fetching fresh flight details for flight ID: {}", flightId);
            
            // This will get the most up-to-date data from flight service
            var flightDetails = flightClient.getFlightDetails(flightId);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", flightDetails,
                "message", "Fresh flight details retrieved successfully"
            ));
        } catch (Exception e) {
            log.error("Error fetching fresh flight details for flight {}: {}", flightId, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Failed to fetch fresh flight details: " + e.getMessage()
            ));
        }
    }
}
