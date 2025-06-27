package com.boeing.loyalty.service;

import com.boeing.loyalty.dto.voucher.UserVoucherListResponseDTO;
import com.boeing.loyalty.dto.voucher.UserVoucherResponseDTO;
import com.boeing.loyalty.dto.voucher.VoucherTemplateResponseDTO;
import com.boeing.loyalty.entity.UserVoucher;
import com.boeing.loyalty.entity.VoucherTemplate;
import com.boeing.loyalty.entity.enums.VoucherStatus;
import com.boeing.loyalty.exception.BadRequestException;
import com.boeing.loyalty.repository.MembershipRepository;
import com.boeing.loyalty.repository.UserVoucherRepository;
import com.boeing.loyalty.repository.VoucherTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserVoucherService {
    private final UserVoucherRepository userVoucherRepository;
    private final VoucherTemplateRepository voucherTemplateRepository;
    private final VoucherTemplateService voucherTemplateService;
    private final MembershipRepository membershipRepository;

    @Transactional(readOnly = true)
    public UserVoucherResponseDTO getUserVoucher(UUID id) {
        UserVoucher userVoucher = userVoucherRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("User voucher not found with id: " + id));
        return mapToResponseDTO(userVoucher);
    }

    @Transactional(readOnly = true)
    public UserVoucherListResponseDTO getUserVouchers(UUID userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<UserVoucher> userVouchers = userVoucherRepository.findByMembership_UserId(userId, pageable);

        return UserVoucherListResponseDTO.builder()
                .userVouchers(userVouchers.getContent().stream()
                        .map(this::mapToResponseDTO)
                        .toList())
                .totalElements((int) userVouchers.getTotalElements())
                .totalPages(userVouchers.getTotalPages())
                .currentPage(page)
                .pageSize(size)
                .build();
    }

    @Transactional
    public UserVoucherResponseDTO redeemVoucher(UUID userId, UUID templateId) {
        VoucherTemplate template = voucherTemplateRepository.findById(templateId)
                .orElseThrow(() -> new BadRequestException("Voucher template not found with id: " + templateId));

        if (template.getStatus() != VoucherStatus.ACTIVE) {
            throw new BadRequestException("Voucher template is not active");
        }

        // TODO: Check if user has enough points
        // TODO: Deduct points from user's balance

        UserVoucher userVoucher = UserVoucher.builder()
                .membership(membershipRepository.findByUserId(userId)
                        .orElseThrow(() -> new BadRequestException("Membership not found for user ID: " + userId)))
                .voucher(template)
                .code(generateVoucherCode())
                .discountAmount(calculateDiscountAmount(template))
                .isUsed(false)
                .expiresAt(template.getEndDate().atTime(23, 59, 59))
                .build();

        userVoucher = userVoucherRepository.save(userVoucher);
        return mapToResponseDTO(userVoucher);
    }

    @Transactional
    public UserVoucherResponseDTO useVoucher(UUID id) {
        UserVoucher userVoucher = userVoucherRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("User voucher not found with id: " + id));

        if (userVoucher.getIsUsed()) {
            throw new BadRequestException("Voucher has already been used");
        }

        if (userVoucher.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Voucher has expired");
        }

        userVoucher.setIsUsed(true);
        userVoucher.setUsedAt(LocalDateTime.now());
        userVoucher = userVoucherRepository.save(userVoucher);
        return mapToResponseDTO(userVoucher);
    }

    private UserVoucherResponseDTO mapToResponseDTO(UserVoucher userVoucher) {
        return UserVoucherResponseDTO.builder()
                .id(userVoucher.getId())
                .membershipId(userVoucher.getMembership().getId())
                .voucherTemplate(voucherTemplateService.mapToResponseDTO(userVoucher.getVoucher()))
                .code(userVoucher.getCode())
                .discountAmount(userVoucher.getDiscountAmount())
                .isUsed(userVoucher.getIsUsed())
                .usedAt(userVoucher.getUsedAt())
                .expiresAt(userVoucher.getExpiresAt())
                .createdAt(userVoucher.getCreatedAt())
                .updatedAt(userVoucher.getUpdatedAt())
                .build();
    }

    private String generateVoucherCode() {
        // TODO: Implement proper voucher code generation
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private Double calculateDiscountAmount(VoucherTemplate template) {
        // TODO: Implement proper discount calculation based on business rules
        return template.getMaxDiscount();
    }
} 