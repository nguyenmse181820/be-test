package com.boeing.loyalty.service;

import java.util.Collections;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.boeing.loyalty.dto.voucher.CreateVoucherTemplateRequestDTO;
import com.boeing.loyalty.dto.voucher.UpdateVoucherTemplateRequestDTO;
import com.boeing.loyalty.dto.voucher.UserVoucherResponseDTO;
import com.boeing.loyalty.dto.voucher.VoucherTemplateListResponseDTO;
import com.boeing.loyalty.dto.voucher.VoucherTemplateResponseDTO;
import com.boeing.loyalty.entity.VoucherTemplate;
import com.boeing.loyalty.entity.enums.VoucherStatus;
import com.boeing.loyalty.exception.BadRequestException;
import com.boeing.loyalty.repository.VoucherTemplateRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VoucherTemplateService {
    private final VoucherTemplateRepository voucherTemplateRepository;

    @Transactional
    public VoucherTemplateResponseDTO createVoucherTemplate(CreateVoucherTemplateRequestDTO request ) {
        VoucherTemplate template = VoucherTemplate.builder()
                .code(request.getCode())
                .name(request.getName())
                .description(request.getDescription())
                .discountPercentage(request.getDiscountPercentage())
                .minSpend(request.getMinSpend())
                .maxDiscount(request.getMaxDiscount())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .usageLimit(request.getUsageLimit())
                .pointsRequired(request.getPointsRequired())
                .status(VoucherStatus.valueOf(request.getStatus()))
                .build();

        template = voucherTemplateRepository.save(template);
        return mapToResponseDTO(template);
    }

    @Transactional(readOnly = true)
    public VoucherTemplateResponseDTO getVoucherTemplate(UUID id) {
        VoucherTemplate template = voucherTemplateRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Voucher template not found with id: " + id));
        return mapToResponseDTO(template);
    }

    @Transactional(readOnly = true)
    public VoucherTemplateListResponseDTO getAllVoucherTemplates(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<VoucherTemplate> templates = voucherTemplateRepository.findAll(pageable);

        return VoucherTemplateListResponseDTO.builder()
                .voucherTemplates(templates.getContent().stream()
                        .map(this::mapToResponseDTO)
                        .toList())
                .totalElements((int) templates.getTotalElements())
                .totalPages(templates.getTotalPages())
                .currentPage(page)
                .pageSize(size)
                .build();
    }

    @Transactional
    public VoucherTemplateResponseDTO updateVoucherTemplate(UUID id, UpdateVoucherTemplateRequestDTO request) {
        VoucherTemplate template = voucherTemplateRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Voucher template not found with id: " + id));

        template.setCode(request.getCode());
        template.setName(request.getName());
        template.setDescription(request.getDescription());
        template.setDiscountPercentage(request.getDiscountPercentage());
        template.setMinSpend(request.getMinSpend());
        template.setMaxDiscount(request.getMaxDiscount());
        template.setStartDate(request.getStartDate());
        template.setEndDate(request.getEndDate());
        template.setUsageLimit(request.getUsageLimit());
        template.setPointsRequired(request.getPointsRequired());
        template.setStatus(VoucherStatus.valueOf(request.getStatus()));

        template = voucherTemplateRepository.save(template);
        return mapToResponseDTO(template);
    }

    @Transactional
    public void deleteVoucherTemplate(UUID id) {
        if (!voucherTemplateRepository.existsById(id)) {
            throw new BadRequestException("Voucher template not found with id: " + id);
        }
        voucherTemplateRepository.deleteById(id);
    }

    VoucherTemplateResponseDTO mapToResponseDTO(VoucherTemplate template) {
        return VoucherTemplateResponseDTO.builder()
                .id(template.getId())
                .code(template.getCode())
                .name(template.getName())
                .description(template.getDescription())
                .discountPercentage(template.getDiscountPercentage())
                .minSpend(template.getMinSpend())
                .maxDiscount(template.getMaxDiscount())
                .startDate(template.getStartDate())
                .endDate(template.getEndDate())
                .usageLimit(template.getUsageLimit())
                .pointsRequired(template.getPointsRequired())
                .status(template.getStatus().name())
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .userVouchers(template.getUserVouchers() != null ? 
                        template.getUserVouchers().stream()
                                .map(userVoucher -> UserVoucherResponseDTO.builder()
                                        .id(userVoucher.getId())
                                        .membershipId(userVoucher.getMembership().getId())
                                        .code(userVoucher.getCode())
                                        .discountAmount(userVoucher.getDiscountAmount())
                                        .isUsed(userVoucher.getIsUsed())
                                        .usedAt(userVoucher.getUsedAt())
                                        .expiresAt(userVoucher.getExpiresAt())
                                        .createdAt(userVoucher.getCreatedAt())
                                        .updatedAt(userVoucher.getUpdatedAt())
                                        .build())
                                .toList() : 
                        Collections.emptyList())
                .build();
    }
} 