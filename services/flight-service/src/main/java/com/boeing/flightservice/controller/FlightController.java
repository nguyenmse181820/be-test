package com.boeing.flightservice.controller;

import com.boeing.flightservice.annotation.StandardAPIResponses;
import com.boeing.flightservice.annotation.StandardGetParams;
import com.boeing.flightservice.dto.request.*;
import com.boeing.flightservice.dto.response.*;
import com.boeing.flightservice.dto.union.Search;
import com.boeing.flightservice.entity.enums.FareType;
import com.boeing.flightservice.service.spec.FlightService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJacksonValue;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "2. Flight", description = "APIs for managing flights")
@RestController
@RequiredArgsConstructor
public class FlightController {

    private final FlightService service;

    @GetMapping("/api/v1/fs/flights")
    @Operation(
            summary = "[DEBUG] Get all flights",
            description = "Get all flights with optional filtering, sorting, and pagination"
    )
    @StandardGetParams
    @StandardAPIResponses
//    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public MappingJacksonValue getAllFlights(@RequestParam Map<String, String> params) {
        return service.findAll(params);
    }

    @PostMapping("/api/v1/fs/flights")
    @Operation(
            summary = "Create a flight with seat class configuration",
            description = "Create a flight using aircraft seat sections instead of manual seat ranges"
    )
    @StandardAPIResponses
    // @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public ResponseEntity<FlightResponseDTO> createFlight(
            @Valid @RequestBody FsFlightCreateRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(service.createFlight(request));
    }

    @PutMapping("/api/v1/fs/flights/{flightId}")
    @Operation(
            summary = "Update an existing flight",
            description = "Update flight details and seat class configuration"
    )
    @StandardAPIResponses
    // @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public ResponseEntity<FlightResponseDTO> updateFlight(
            @PathVariable UUID flightId,
            @Valid @RequestBody FsFlightCreateRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(service.updateFlight(flightId, request));
    }

    @GetMapping("/api/v1/fs/flights/{flightId}")
    @Operation(
            summary = "Get flight by ID",
            description = "Retrieve detailed flight information by ID"
    )
    @StandardAPIResponses
    // @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public ResponseEntity<FlightResponseDTO> getFlightById(
            @PathVariable UUID flightId
    ) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(service.getFlightById(flightId));
    }

    @GetMapping("/api/v1/fs/flights/{flightId}/seats/check-availability")
    @Operation(summary = "Kiểm tra ghế trước khi tạo booking (Saga step).", description = "Kiểm tra ghế trước khi tạo booking (Saga step).")
    @StandardAPIResponses
    public ResponseEntity<FsSeatsAvailabilityResponseDTO> checkSeatAvailability(
            @PathVariable UUID flightId,
            @RequestParam List<String> seatCodes
    ) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(
                        service.checkSeatAvailability(
                                flightId,
                                seatCodes
                        )
                );
    }

    @PostMapping("/api/v1/fs/flights/search")
    @Operation(
            summary = "Tìm kiếm máy bay",
            description = "Tìm kiếm máy bay"
    )
    @StandardAPIResponses
    public ResponseEntity<Search.Response> searchFlights(
            @RequestBody Search.Request request
    ) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(
                        service.searchFlights(request)
                );
    }

    @GetMapping("/api/v1/fs/flights/{flightId}/details")
    @Operation(summary = "Lấy chi tiết chuyến bay để tạo snapshot và xác minh giá/điều kiện vé (Saga step).", description = "Lấy chi tiết chuyến bay để tạo snapshot và xác minh giá/điều kiện vé (Saga step).")
    @StandardAPIResponses
    public ResponseEntity<FsFlightWithFareDetailsDTO> getFlightDetails(
            @PathVariable UUID flightId
    ) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(
                        service.getFlightDetails(
                                flightId
                        )
                );
    }

    @PostMapping("/api/v1/fs/flights/{flightId}/seats/confirm")
    @Operation(summary = "Xác nhận ghế sau khi thanh toán thành công (Saga step).", description = "Xác nhận ghế sau khi thanh toán thành công (Saga step).")
    @StandardAPIResponses
//    @PreAuthorize("hasAnyRole('ADMIN','STAFF','USER')")
    public ResponseEntity<FsConfirmSeatsResponseDTO> confirmSeat(
            @PathVariable UUID flightId,
            @RequestBody FsConfirmSeatsRequestDTO request
    ) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(
                        service.confirmSeat(
                                flightId,
                                request
                        )
                );
    }

    @PostMapping("/api/v1/fs/flights/{flightId}/seats/release")
    @Operation(summary = "Giải phóng ghế (hủy vé, scheduler hủy, Saga rollback).", description = "Giải phóng ghế (hủy vé, scheduler hủy, Saga rollback).")
    @StandardAPIResponses
//    @PreAuthorize("hasAnyRole('ADMIN','STAFF','USER')")
    public ResponseEntity<FsReleaseSeatsResponseDTO> releaseSeat(
            @PathVariable UUID flightId,
            @RequestBody FsReleaseSeatsRequestDTO request
    ) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(
                        service.releaseSeats(
                                flightId,
                                request
                        )
                );
    }

    // Deprecated APIs for backward compatibility
    @GetMapping("/api/v1/fs/aircraft/{aircraftId}/seat-sections")
    @Operation(
            summary = "Get aircraft seat sections",
            description = "Get seat sections/classes available for the specified aircraft",
            deprecated = true
    )
    @StandardAPIResponses
    @Deprecated
//    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getAircraftSeatSections(
            @PathVariable UUID aircraftId
    ) {
        Map<FareType, List<String>> seatSections = service.getAircraftSeatSections(aircraftId);
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("aircraftId", aircraftId);
        response.put("seatSections", seatSections);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/v1/fs/flights/{flightId}/available-seats-count")
    @Operation(
            summary = "Get available seats count for a flight and fare class",
            description = "Get the number of available seats for a specific flight and fare class",
            deprecated = true
    )
    @StandardAPIResponses
    @Deprecated
//    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getAvailableSeatsCount(
            @PathVariable UUID flightId,
            @RequestParam String fareClass
    ) {
        int availableSeats = service.getAvailableSeatsCount(flightId, fareClass);
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("flightId", flightId);
        response.put("fareClass", fareClass);
        response.put("availableSeats", availableSeats);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/v1/fs/flights/{flightId}/fares/{fareName}/confirm-sale")
    @Operation(
            summary = "Xác nhận đã bán vé và giảm số lượng ghế trống cho hạng vé đó sau khi thanh toán thành công (Saga step).",
            description = "Xác nhận đã bán vé và giảm số lượng ghế trống cho hạng vé đó sau khi thanh toán thành công (Saga step).",
            deprecated = true
    )
    @StandardAPIResponses
    @Deprecated
//    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<FsConfirmFareSaleResponseDTO> confirmFareSale(
            @PathVariable("flightId") UUID flightId,
            @PathVariable("fareName") String fareName,
            @RequestBody FsConfirmFareSaleRequestDTO request
    ) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(service.confirmFareSale(flightId, fareName, request));
    }

    @PostMapping("/api/v1/fs/flights/{flightId}/fares/{fareName}/release")
    @Operation(
            summary = "Giải phóng/tăng lại số lượng ghế trống cho hạng vé (khi hủy vé, scheduler hủy booking PENDING_PAYMENT, Saga rollback).",
            description = "Giải phóng/tăng lại số lượng ghế trống cho hạng vé (khi hủy vé, scheduler hủy booking PENDING_PAYMENT, Saga rollback).",
            deprecated = true
    )
    @StandardAPIResponses
    @Deprecated
//    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<FsReleaseFareResponseDTO> releaseFare(
            @PathVariable("flightId") UUID flightId,
            @PathVariable("fareName") String fareName,
            @RequestBody FsReleaseFareRequestDTO request
    ) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(service.releaseFare(flightId, fareName, request));
    }
}
