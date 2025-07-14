package com.boeing.loyalty.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.boeing.loyalty.dto.voucher.UserVoucherListResponseDTO;
import com.boeing.loyalty.dto.voucher.UserVoucherResponseDTO;
import com.boeing.loyalty.entity.LoyaltyPointTransaction;
import com.boeing.loyalty.entity.Membership;
import com.boeing.loyalty.entity.UserVoucher;
import com.boeing.loyalty.entity.VoucherTemplate;
import com.boeing.loyalty.entity.enums.PointType;
import com.boeing.loyalty.entity.enums.VoucherStatus;
import com.boeing.loyalty.exception.BadRequestException;
import com.boeing.loyalty.repository.LoyaltyPointTransactionRepository;
import com.boeing.loyalty.repository.MembershipRepository;
import com.boeing.loyalty.repository.UserVoucherRepository;
import com.boeing.loyalty.repository.VoucherTemplateRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserVoucherService {
    private final UserVoucherRepository userVoucherRepository;
    private final VoucherTemplateRepository voucherTemplateRepository;
    private final VoucherTemplateService voucherTemplateService;
    private final MembershipRepository membershipRepository;
    private final LoyaltyPointTransactionRepository loyaltyPointTransactionRepository;

    @Transactional(readOnly = true)
    public UserVoucherResponseDTO getUserVoucher(UUID id) {
        UserVoucher userVoucher = userVoucherRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("User voucher not found with id: " + id));
        return mapToResponseDTO(userVoucher);
    }

    @Transactional(readOnly = true)
    public UserVoucherListResponseDTO getUserVouchers(UUID userId, int page, int size) {
        // FIX 8: Added input validation
        if (userId == null) {
            throw new BadRequestException("User ID cannot be null");
        }
        if (page < 0) {
            throw new BadRequestException("Page number cannot be negative");
        }
        if (size <= 0 || size > 100) {
            throw new BadRequestException("Page size must be between 1 and 100");
        }
        
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

        // Get user's membership
        Membership membership = membershipRepository.findByUserId(userId)
                .orElseThrow(() -> new BadRequestException("Membership not found for user ID: " + userId));

        // Check if user has enough points
        int pointsRequired = template.getPointsRequired();
        if (membership.getPoints() < pointsRequired) {
            throw new BadRequestException(String.format("Insufficient points. Required: %d, Available: %d", 
                pointsRequired, membership.getPoints()));
        }

        // Deduct points from user's balance
        membership.setPoints(membership.getPoints() - pointsRequired);
        membershipRepository.save(membership);

        // Create a transaction record for the point deduction
        LoyaltyPointTransaction transaction = LoyaltyPointTransaction.builder()
                .type(PointType.REDEEM)
                .source("VOUCHER_REDEMPTION")
                .points(pointsRequired)
                .note("Redeemed voucher: " + template.getName())
                .membership(membership)
                .createdAt(LocalDateTime.now())
                .build();
        loyaltyPointTransactionRepository.save(transaction);

        UserVoucher userVoucher = UserVoucher.builder()
                .membership(membership)
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
        // FIX 8: Implemented proper voucher code generation with uniqueness check
        // The previous TODO implementation could generate duplicate codes
        String code;
        int attempts = 0;
        int maxAttempts = 10;
        
        do {
            code = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            attempts++;
            
            // FIX 8: Added safety check to prevent infinite loops
            if (attempts >= maxAttempts) {
                throw new IllegalStateException("Failed to generate unique voucher code after " + maxAttempts + " attempts");
            }
        } while (userVoucherRepository.findByCode(code).isPresent());
        
        return code;
    }

    private Double calculateDiscountAmount(VoucherTemplate template) {
        // This method calculates the maximum possible discount for the voucher
        // The actual discount will be calculated during booking based on purchase amount
        // For now, we store the maximum discount amount as the voucher's value
        return template.getMaxDiscount();
    }
    
    /**
     * Calculates the actual discount amount based on purchase amount and voucher template
     */
    public Double calculateActualDiscount(VoucherTemplate template, Double purchaseAmount) {
        if (purchaseAmount == null || purchaseAmount <= 0) {
            return 0.0;
        }
        
        // Check minimum spend requirement
        if (purchaseAmount < template.getMinSpend()) {
            return 0.0;
        }
        
        // Calculate percentage-based discount
        Double percentageDiscount = (purchaseAmount * template.getDiscountPercentage()) / 100.0;
        
        // Apply maximum discount cap
        return Math.min(percentageDiscount, template.getMaxDiscount());
    }
} 