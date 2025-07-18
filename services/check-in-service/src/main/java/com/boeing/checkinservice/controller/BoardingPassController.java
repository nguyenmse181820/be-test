package com.boeing.checkinservice.controller;

import com.boeing.checkinservice.dto.requests.AddBoardingPassDto;
import com.boeing.checkinservice.dto.responses.ApiResponse;
import com.boeing.checkinservice.dto.responses.BookingDetailInfoDTO;
import com.boeing.checkinservice.service.inte.BoardingPassService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/boarding-pass")
@Validated
public class BoardingPassController {

    private final BoardingPassService boardingPassService;

    @PostMapping("/processing-get-all-boarding-pass")
    public ResponseEntity<?> getAllBoardingPass(@RequestBody List<BookingDetailInfoDTO> details) {
        return ResponseEntity.ok(ApiResponse
                .builder()
                .success(true)
                .message("Get All Boarding Pass Successfully")
                .data(boardingPassService.getAllBoardingPassesByBookingReference(details))
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
    public ResponseEntity<?> createBoardingPass(@RequestBody @Valid List<AddBoardingPassDto> addBoardingPassDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse
                        .builder()
                        .success(true)
                        .message("Add new Boarding Pass Successfully")
                        .data(boardingPassService.addNewBoardingPass(addBoardingPassDto))
                        .build()
        );
    }

    @GetMapping("/check-in-status")
    public ResponseEntity<?> checkInStatus(@RequestParam("booking_detail_ids") List<UUID> bookingDetailIds) {
        return ResponseEntity.ok(ApiResponse.builder()
                        .message("Check In Status Successfully")
                        .data(boardingPassService.checkInStatus(bookingDetailIds))
                .build());
    }
}
