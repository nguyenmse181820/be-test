package com.boeing.checkinservice.controller;

import com.boeing.checkinservice.dto.requests.BaggageDto;
import com.boeing.checkinservice.dto.responses.ApiResponse;
import com.boeing.checkinservice.service.inte.BaggageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/baggage")
@RequiredArgsConstructor
public class BaggageController {

    private final BaggageService baggageService;

    @GetMapping
    public ResponseEntity<?> getBaggage() {
        return ResponseEntity.ok(ApiResponse
                .builder()
                .success(true)
                .message("Get All Baggage Successfully")
                .data(baggageService.getAllBaggage())
                .build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getBaggageById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse
                .builder()
                .success(true)
                .message("Get Baggage Successfully")
                .data(baggageService.getBaggageById(id))
                .build());
    }

    @PostMapping
    public ResponseEntity<?> createBaggage(@RequestParam("boarding_pass_id") UUID boardingPassId, @RequestBody List<BaggageDto> baggageDto) {
        return ResponseEntity.ok(ApiResponse.builder()
                        .success(true)
                        .data(baggageService.addBaggage(boardingPassId, baggageDto))
                        .message("Create Baggage Successfully")
                .build());
    }
}
