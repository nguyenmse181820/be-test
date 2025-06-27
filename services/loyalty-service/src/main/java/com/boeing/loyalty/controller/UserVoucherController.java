package com.boeing.loyalty.controller;

import com.boeing.loyalty.dto.voucher.UserVoucherListResponseDTO;
import com.boeing.loyalty.dto.voucher.UserVoucherResponseDTO;
import com.boeing.loyalty.service.UserVoucherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/user-vouchers")
@RequiredArgsConstructor
@Tag(name = "User Voucher", description = "User voucher management APIs")
public class UserVoucherController {
    private final UserVoucherService userVoucherService;

    @GetMapping("/{id}")
    @Operation(summary = "Get a user voucher by ID")
    public ResponseEntity<UserVoucherResponseDTO> getUserVoucher(
            @Parameter(description = "User voucher ID") @PathVariable UUID id) {
        return ResponseEntity.ok(userVoucherService.getUserVoucher(id));
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get all vouchers for a user")
    public ResponseEntity<UserVoucherListResponseDTO> getUserVouchers(
            @Parameter(description = "User ID") @PathVariable UUID userId,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(userVoucherService.getUserVouchers(userId, page, size));
    }

    @PostMapping("/user/{userId}/redeem/{templateId}")
    @Operation(summary = "Redeem a voucher template for a user")
    public ResponseEntity<UserVoucherResponseDTO> redeemVoucher(
            @Parameter(description = "User ID") @PathVariable UUID userId,
            @Parameter(description = "Voucher template ID") @PathVariable UUID templateId) {
        return ResponseEntity.ok(userVoucherService.redeemVoucher(userId, templateId));
    }

    @PostMapping("/{id}/use")
    @Operation(summary = "Mark a voucher as used")
    public ResponseEntity<UserVoucherResponseDTO> useVoucher(
            @Parameter(description = "User voucher ID") @PathVariable UUID id) {
        return ResponseEntity.ok(userVoucherService.useVoucher(id));
    }
} 