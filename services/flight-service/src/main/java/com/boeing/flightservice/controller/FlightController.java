package com.boeing.flightservice.controller;

import java.util.List;
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
import com.boeing.flightservice.dto.request.FsConfirmSeatsRequestDTO;
import com.boeing.flightservice.dto.request.FsFlightCreateRequestV2;
import com.boeing.flightservice.dto.request.FsReleaseSeatsRequestDTO;
import com.boeing.flightservice.dto.response.FlightResponseDTO;
import com.boeing.flightservice.dto.response.FsConfirmSeatsResponseDTO;
import com.boeing.flightservice.dto.response.FsFlightWithFareDetailsDTO;
import com.boeing.flightservice.dto.response.FsReleaseSeatsResponseDTO;
import com.boeing.flightservice.dto.response.FsSeatsAvailabilityResponseDTO;
import com.boeing.flightservice.service.spec.FlightService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "2. Flight", description = "APIs for managing flights")
@RestController
@RequiredArgsConstructor
public class FlightController {
    private final FlightService service;

    @GetMapping("/api/v1/fs/flights")
    @Operation(summary = "Get all flights", description = "Get all flights with optional filtering, sorting, and pagination")
    @StandardGetParams
    @StandardAPIResponses
    public MappingJacksonValue getAllBenefits(@RequestParam Map<String, String> params) {
        return service.findAll(params);
    }

//    @PostMapping("/api/v1/fs/flights")
//    @Operation(summary = "Create a flight", description = "Create a flight")
//    @StandardAPIResponses
//    public ResponseEntity<FlightResponseDTO> createFlight(
//            @RequestBody FsFlightCreateRequest fsFlightCreateRequest
//    ) {
//        return ResponseEntity
//                .status(HttpStatus.CREATED)
//                .body(
//                        service.createFlight(
//                                fsFlightCreateRequest
//                        )
//                );
//    }

    @PostMapping("/api/v1/fs/flights")
    @Operation(summary = "Create a flight with seat class configuration", description = "Create a flight using aircraft seat sections instead of manual seat ranges")
    @StandardAPIResponses
    public ResponseEntity<FlightResponseDTO> createFlightV2(
            @RequestBody FsFlightCreateRequestV2 request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(service.createFlightV2(request));
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
    public ResponseEntity<FsReleaseSeatsResponseDTO> confirmSeat(
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

    @GetMapping("/api/v1/fs/flights/{flightId}/available-seats-count")
    @Operation(summary = "Get available seats count for a flight and fare class", description = "Get the number of available seats for a specific flight and fare class")
    @StandardAPIResponses
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

    @GetMapping("/api/v1/fs/aircraft/{aircraftId}/seat-sections")
    @Operation(summary = "Get aircraft seat sections", description = "Get seat sections/classes available for the specified aircraft")
    @StandardAPIResponses
    public ResponseEntity<Map<String, Object>> getAircraftSeatSections(
            @PathVariable UUID aircraftId
    ) {
        Map<String, List<String>> seatSections = service.getAircraftSeatSections(aircraftId);
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("aircraftId", aircraftId);
        response.put("seatSections", seatSections);
        return ResponseEntity.ok(response);
    }
}
