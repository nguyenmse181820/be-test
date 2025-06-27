package com.boeing.bookingservice.controller;

import com.boeing.bookingservice.dto.request.PassengerInfoDTO;
import com.boeing.bookingservice.security.AuthenticatedUserPrincipal;
import com.boeing.bookingservice.service.PassengerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@Slf4j
@RequestMapping("/api/v1/passengers")
@Tag(name = "Passenger Management", description = "APIs for managing passenger information")
public class PassengerController {

    private final PassengerService passengerService;

    @Autowired
    public PassengerController(PassengerService passengerService) {
        this.passengerService = passengerService;
    }

    @Operation(summary = "Get all passengers for a specific user", description = "Retrieves a list of all passengers associated with the given user ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved list of passengers"),
            @ApiResponse(responseCode = "400", description = "Invalid user ID supplied"),
            @ApiResponse(responseCode = "404", description = "User not found or no passengers for this user")
    })
    @GetMapping("/user")
    public ResponseEntity<List<PassengerInfoDTO>> getPassengersByUser(
            @Parameter(description = "ID of the user to retrieve passengers for", required = true) @RequestParam UUID userId) {
        List<PassengerInfoDTO> passengers = passengerService.getPassengersByUser(userId);
        return ResponseEntity.ok(passengers);
    }

    @Operation(summary = "Create a new passenger", description = "Creates a new passenger record.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Passenger created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid passenger data supplied")
    })
    @PostMapping
    public ResponseEntity<PassengerInfoDTO> createPassenger(
            @Parameter(description = "Passenger object that needs to be added", required = true)
            @RequestBody PassengerInfoDTO passengerInfoDTO) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UUID currentUserId = getUserIdFromAuthentication(authentication);
        PassengerInfoDTO createdPassenger = passengerService.createPassenger(passengerInfoDTO, currentUserId);
        return ResponseEntity.status(201).body(createdPassenger);
    }

    @Operation(summary = "Update an existing passenger", description = "Updates the details of an existing passenger identified by their ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Passenger updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid passenger data supplied"),
            @ApiResponse(responseCode = "404", description = "Passenger not found with the given ID")
    })
    @PutMapping("/{id}")
    public ResponseEntity<PassengerInfoDTO> updatePassenger(
            @Parameter(description = "ID of the passenger to update", required = true) @PathVariable UUID id,
            @Parameter(description = "Passenger object with updated information", required = true) @RequestBody PassengerInfoDTO passengerInfoDTO) {
        PassengerInfoDTO updatedPassenger = passengerService.updatePassenger(id, passengerInfoDTO);
        return ResponseEntity.ok(updatedPassenger);
    }

    private UUID getUserIdFromAuthentication(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("User is not authenticated.");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof AuthenticatedUserPrincipal) {
            return ((AuthenticatedUserPrincipal) principal).getUserIdAsUUID();
        }
        log.warn("Could not extract UUID UserId from principal of type: {}. Principal: {}", principal.getClass().getName(), principal);
        throw new AccessDeniedException("Cannot determine user ID from authentication principal.");
    }
}
