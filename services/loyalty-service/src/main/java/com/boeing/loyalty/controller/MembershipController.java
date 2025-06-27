package com.boeing.loyalty.controller;

import com.boeing.loyalty.dto.APIResponse;
import com.boeing.loyalty.dto.membership.CreateMembershipRequestDTO;
import com.boeing.loyalty.dto.membership.UpdateMembershipRequestDTO;
import com.boeing.loyalty.service.MembershipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/memberships")
@RequiredArgsConstructor
@Tag(name = "Membership Management", description = "APIs for managing loyalty memberships")
public class MembershipController {

    private final MembershipService membershipService;

    @Operation(summary = "Create a new membership", description = "Creates a new loyalty membership for a user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Membership created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request or membership already exists")
    })
    @PostMapping
    public ResponseEntity<APIResponse> createMembership(
            @Parameter(description = "Membership creation details", required = true)
            @Valid @RequestBody CreateMembershipRequestDTO request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(APIResponse.builder()
                        .statusCode(HttpStatus.CREATED.value())
                        .data(membershipService.createMembership(request))
                        .build());
    }

    @Operation(summary = "Get membership by ID", description = "Retrieves a membership by its ID, including point transactions")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Membership found"),
            @ApiResponse(responseCode = "400", description = "Membership not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<APIResponse> getMembership(
            @Parameter(description = "Membership ID", required = true)
            @PathVariable UUID id) {
        return ResponseEntity.ok(APIResponse.builder()
                .statusCode(HttpStatus.OK.value())
                .data(membershipService.getMembership(id))
                .build());
    }

    @Operation(summary = "Get membership by user ID", description = "Retrieves a membership by user ID, including point transactions")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Membership found"),
            @ApiResponse(responseCode = "400", description = "Membership not found")
    })
    @GetMapping("/user/{userId}")
    public ResponseEntity<APIResponse> getMembershipByUserId(
            @Parameter(description = "User ID", required = true)
            @PathVariable UUID userId) {
        return ResponseEntity.ok(APIResponse.builder()
                .statusCode(HttpStatus.OK.value())
                .data(membershipService.getMembershipByUserId(userId))
                .build());
    }

    @Operation(summary = "Get all memberships", description = "Retrieves all memberships, including their point transactions")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved all memberships")
    })
    @GetMapping
    public ResponseEntity<APIResponse> getAllMemberships() {
        return ResponseEntity.ok(APIResponse.builder()
                .statusCode(HttpStatus.OK.value())
                .data(membershipService.getAllMemberships())
                .build());
    }

    @Operation(summary = "Update membership", description = "Updates an existing membership")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Membership updated successfully"),
            @ApiResponse(responseCode = "400", description = "Membership not found or invalid request")
    })
    @PutMapping("/{id}")
    public ResponseEntity<APIResponse> updateMembership(
            @Parameter(description = "Membership ID", required = true)
            @PathVariable UUID id,
            @Parameter(description = "Membership update details", required = true)
            @Valid @RequestBody UpdateMembershipRequestDTO request) {
        return ResponseEntity.ok(APIResponse.builder()
                .statusCode(HttpStatus.OK.value())
                .data(membershipService.updateMembership(id, request))
                .build());
    }

    @Operation(summary = "Delete membership", description = "Deletes a membership")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Membership deleted successfully"),
            @ApiResponse(responseCode = "400", description = "Membership not found")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<APIResponse> deleteMembership(
            @Parameter(description = "Membership ID", required = true)
            @PathVariable UUID id) {
        membershipService.deleteMembership(id);
        return ResponseEntity.ok(APIResponse.builder()
                .statusCode(HttpStatus.NO_CONTENT.value())
                .data("Membership deleted successfully")
                .build());
    }
}