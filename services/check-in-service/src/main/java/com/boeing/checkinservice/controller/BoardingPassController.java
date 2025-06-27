package com.boeing.checkinservice.controller;

import com.boeing.checkinservice.dto.requests.AddBoardingPassDto;
import com.boeing.checkinservice.dto.responses.ApiResponse;
import com.boeing.checkinservice.service.inte.BoardingPassService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/boarding-pass")
public class BoardingPassController {

    private final BoardingPassService boardingPassService;

    @GetMapping
    public ResponseEntity<?> getAllBoardingPass() {
        return ResponseEntity.ok(ApiResponse
                .builder()
                .success(true)
                .message("Get All Boarding Pass Successfully")
                .data(boardingPassService.getAllBoardingPasses())
                .build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getBoardingPassById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse
                .builder()
                .success(true)
                .message("Get Boarding Pass Successfully")
                .data(boardingPassService.getBoardingPassById(id))
                .build());
    }

    @PostMapping
    public ResponseEntity<?> createBoardingPass(@RequestBody @Valid AddBoardingPassDto addBoardingPassDto,
                                                @RequestParam(name = "flightId") UUID flightId,@RequestParam(name = "booking_detail_id") UUID booking_detail_id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse
                        .builder()
                        .success(true)
                        .message("Add new Boarding Pass Successfully")
                        .data(boardingPassService.addNewBoardingPass(addBoardingPassDto, flightId, booking_detail_id))
                        .build()
        );
    }

    @GetMapping("/check-in-status")
    public ResponseEntity<?> checkInStatus(@RequestParam("booking_detail_id") UUID booking_detail_id) {
        return ResponseEntity.ok(ApiResponse.builder()
                        .message("Check In Status Successfully")
                        .data(boardingPassService.checkInStatus(booking_detail_id))
                .build());
    }
}
