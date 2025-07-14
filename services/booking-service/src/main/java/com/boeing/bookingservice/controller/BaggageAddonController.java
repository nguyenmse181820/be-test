package com.boeing.bookingservice.controller;

import com.boeing.bookingservice.dto.request.BaggageAddonRequestDTO;
import com.boeing.bookingservice.dto.response.ApiResponse;
import com.boeing.bookingservice.dto.response.BaggageAddonResponseDTO;
import com.boeing.bookingservice.service.BaggageAddonService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/baggage-addons")
@Tag(name = "Baggage Add-ons", description = "APIs for managing baggage add-ons")
@RequiredArgsConstructor
@Slf4j
public class BaggageAddonController {

    private final BaggageAddonService baggageAddonService;

    @PostMapping("/booking/{bookingId}")
    @Operation(summary = "Add baggage to existing booking", description = "Purchase additional baggage for an existing booking")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Baggage addons added successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request data"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Booking not found")
    })
    public ResponseEntity<ApiResponse<List<BaggageAddonResponseDTO>>> addBaggageToBooking(
            @Parameter(description = "Booking ID", required = true) @PathVariable UUID bookingId,
            @Parameter(description = "List of baggage addons to add", required = true) @Valid @RequestBody List<BaggageAddonRequestDTO> baggageAddons
    ) {
        log.info("Adding baggage addons to booking: {}", bookingId);
        
        List<BaggageAddonResponseDTO> result = baggageAddonService.addBaggageToBooking(bookingId, baggageAddons);
        
        return ResponseEntity.ok(ApiResponse.<List<BaggageAddonResponseDTO>>builder()
                .success(true)
                .message("Baggage addons added successfully")
                .data(result)
                .build());
    }

    @GetMapping("/booking/{bookingId}")
    @Operation(summary = "Get baggage addons for booking", description = "Retrieve all baggage addons for a specific booking")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Baggage addons retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Booking not found")
    })
    public ResponseEntity<ApiResponse<List<BaggageAddonResponseDTO>>> getBaggageAddons(
            @Parameter(description = "Booking ID", required = true) @PathVariable UUID bookingId
    ) {
        log.info("Getting baggage addons for booking: {}", bookingId);
        
        List<BaggageAddonResponseDTO> result = baggageAddonService.getBaggageAddons(bookingId);
        
        return ResponseEntity.ok(ApiResponse.<List<BaggageAddonResponseDTO>>builder()
                .success(true)
                .message("Baggage addons retrieved successfully")
                .data(result)
                .build());
    }
}
