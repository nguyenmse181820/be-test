package com.boeing.loyalty.controller;

import com.boeing.loyalty.dto.voucher.CreateVoucherTemplateRequestDTO;
import com.boeing.loyalty.dto.voucher.UpdateVoucherTemplateRequestDTO;
import com.boeing.loyalty.dto.voucher.VoucherTemplateListResponseDTO;
import com.boeing.loyalty.dto.voucher.VoucherTemplateResponseDTO;
import com.boeing.loyalty.service.VoucherTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vouchers")
@RequiredArgsConstructor
@Tag(name = "Voucher Template", description = "Voucher template management APIs")
public class VoucherTemplateController {
    private final VoucherTemplateService voucherTemplateService;

    @PostMapping
    @Operation(summary = "Create a new voucher template")
    public ResponseEntity<VoucherTemplateResponseDTO> createVoucherTemplate(
            @Valid @RequestBody CreateVoucherTemplateRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(voucherTemplateService.createVoucherTemplate(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a voucher template by ID")
    public ResponseEntity<VoucherTemplateResponseDTO> getVoucherTemplate(
            @Parameter(description = "Voucher template ID") @PathVariable UUID id) {
        return ResponseEntity.ok(voucherTemplateService.getVoucherTemplate(id));
    }

    @GetMapping
    @Operation(summary = "Get all voucher templates")
    public ResponseEntity<VoucherTemplateListResponseDTO> getAllVoucherTemplates(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(voucherTemplateService.getAllVoucherTemplates(page, size));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a voucher template")
    public ResponseEntity<VoucherTemplateResponseDTO> updateVoucherTemplate(
            @Parameter(description = "Voucher template ID") @PathVariable UUID id,
            @Valid @RequestBody UpdateVoucherTemplateRequestDTO request) {
        return ResponseEntity.ok(voucherTemplateService.updateVoucherTemplate(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a voucher template")
    public ResponseEntity<Void> deleteVoucherTemplate(
            @Parameter(description = "Voucher template ID") @PathVariable UUID id) {
        voucherTemplateService.deleteVoucherTemplate(id);
        return ResponseEntity.noContent().build();
    }
} 