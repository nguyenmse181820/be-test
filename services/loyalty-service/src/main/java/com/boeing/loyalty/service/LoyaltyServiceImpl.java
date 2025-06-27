package com.boeing.loyalty.service;

import com.boeing.loyalty.dto.membership.EarnPointsRequestDTO;
import com.boeing.loyalty.dto.membership.EarnPointsResponseDTO;
import com.boeing.loyalty.dto.membership.MembershipResponseDTO;
import com.boeing.loyalty.dto.membership.PointTransactionResponseDTO;
import com.boeing.loyalty.dto.voucher.*;
import com.boeing.loyalty.dto.voucher.UseVoucherResponseDTO;
import com.boeing.loyalty.entity.LoyaltyPointTransaction;
import com.boeing.loyalty.entity.Membership;
import com.boeing.loyalty.entity.UserVoucher;
import com.boeing.loyalty.entity.VoucherTemplate;
import com.boeing.loyalty.entity.enums.MembershipTier;
import com.boeing.loyalty.entity.enums.PointType;
import com.boeing.loyalty.exception.BadRequestException;
import com.boeing.loyalty.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LoyaltyServiceImpl implements LoyaltyService {

    private final MembershipRepository membershipRepository;
    private final UserVoucherRepository userVoucherRepository;
    private final RewardRedemptionRepository rewardRedemptionRepository;
    private final VoucherTemplateRepository voucherTemplateRepository;
    private final LoyaltyPointTransactionRepository loyaltyPointTransactionRepository;
    private final RewardItemRepository rewardItemRepository;

    @Value("${membership.silver.rate}")
    private static float silverRate;
    @Value("${membership.gold.rate}")
    private static float goldRate;
    @Value("${membership.platinum.rate}")
    private static float platinumRate;

    @Override
    public EarnPointsResponseDTO earnPoints(EarnPointsRequestDTO requestDTO) {
        Membership membership = membershipRepository.findByUserId(requestDTO.getUserId()).orElseThrow(() -> new BadRequestException("Membership not found for user ID: " + requestDTO.getUserId()));

        int pointsEarned = calculatePoints(membership, requestDTO.getAmountSpent());

        LoyaltyPointTransaction transaction = LoyaltyPointTransaction.builder()
                .type(PointType.EARN)
                .source(requestDTO.getBookingReference())
                .points(pointsEarned)
                .note("Earn points for transaction: " + requestDTO.getBookingReference())
                .membership(membership)
                .build();
        loyaltyPointTransactionRepository.save(transaction);

        membership.setPoints(membership.getPoints() + pointsEarned);
        membership.setTotalEarnedPoints(membership.getTotalEarnedPoints() + pointsEarned);
        membershipRepository.save(membership);

        return EarnPointsResponseDTO.builder()
                .status("SUCCESS")
                .pointsEarnedThisTransaction((long) pointsEarned)
                .message("Points earned successfully")
                .build();
    }

    @Override
    @Transactional
    public UseVoucherResponseDTO useVoucher(String voucherCode) {
        UserVoucher voucher = userVoucherRepository.findByCode(voucherCode).orElseThrow(() -> new BadRequestException("Invalid or already used voucher code: " + voucherCode));
        voucher.setIsUsed(true);
        voucher.setUsedAt(LocalDateTime.now());
        userVoucherRepository.save(voucher);
        return UseVoucherResponseDTO.builder()
                .status("SUCCESS")
                .message("Voucher used successfully")
                .build();
    }

    @Override
    public List<UserVoucherDTO> getAllVoucher(UUID userId) {
        List<UserVoucher> vouchers = userVoucherRepository.findByIsUsedFalseAndMembership_UserId(userId);
        if (vouchers.isEmpty()) {
            return null;
        }
        return vouchers.stream()
                .map(voucher -> UserVoucherDTO.builder()
                        .code(voucher.getCode())
                        .name(voucher.getVoucher().getName())
                        .discountPercentage(voucher.getVoucher().getDiscountPercentage())
                        .minimumPurchaseAmount(voucher.getVoucher().getMinSpend())
                        .maximumDiscountAmount(voucher.getVoucher().getMaxDiscount())
                        .build())
                .toList();
    }

    @Override
    public String adjustPoints(String bookingId) {
        LoyaltyPointTransaction transaction = loyaltyPointTransactionRepository.findBySource(bookingId);
        if (transaction == null) {
            throw new BadRequestException("Transaction not found for booking ID: " + bookingId);
        }
        Membership membership = transaction.getMembership();
        membership.setPoints(membership.getPoints() - transaction.getPoints());
        membership.setTotalEarnedPoints(membership.getTotalEarnedPoints() - transaction.getPoints());
        membershipRepository.save(membership);
        transaction.setType(PointType.CANCEL);
        loyaltyPointTransactionRepository.save(transaction);
        return "Points adjusted successfully for booking ID: " + bookingId;
    }

    private Integer calculatePoints(Membership membership, BigDecimal amount) {
        MembershipTier tier = MembershipTier.fromPoints(membership.getPoints());
        float rate = switch (tier) {
            case GOLD -> goldRate;
            case PLATINUM -> platinumRate;
            default -> silverRate;
        };
        return amount.multiply(BigDecimal.valueOf(rate)).intValue();
    }

    private MembershipResponseDTO mapToMembershipResponseDTO(Membership membership, List<LoyaltyPointTransaction> transactions) {
        return MembershipResponseDTO.builder()
                .id(membership.getId())
                .userId(membership.getUserId())
                .points(membership.getPoints())
                .tier(membership.getTier())
                .transactions(transactions.stream()
                        .map(this::mapToPointTransactionResponseDTO)
                        .toList())
                .build();
    }

    private PointTransactionResponseDTO mapToPointTransactionResponseDTO(LoyaltyPointTransaction transaction) {
        return PointTransactionResponseDTO.builder()
                .id(transaction.getId())
                .membershipId(transaction.getMembership().getId())
                .points(transaction.getPoints())
                .note(transaction.getNote())
                .createdAt(transaction.getCreatedAt())
                .type(transaction.getType())
                .source(transaction.getSource())
                .build();
    }

//    private UserVoucherResponseDTO mapToUserVoucherResponseDTO(UserVoucher voucher) {
//        return UserVoucherResponseDTO.builder()
//                .id(voucher.getId())
//                .userId(voucher.getUserId())
//                .voucherTemplate(mapToVoucherTemplateResponseDTO(voucher.getVoucher())) // Map entity to DTO
//                .code(voucher.getCode())
//                .discountAmount(voucher.getDiscountAmount())
//                .isUsed(voucher.getIsUsed())
//                .usedAt(voucher.getUsedAt())
//                .expiresAt(voucher.getExpiresAt())
//                .createdAt(voucher.getCreatedAt())
//                .updatedAt(voucher.getUpdatedAt())
//                .build();
//    }
//
//    private VoucherTemplateResponseDTO mapToVoucherTemplateResponseDTO(VoucherTemplate voucherTemplate) {
//        return VoucherTemplateResponseDTO.builder()
//                .id(voucherTemplate.getId())
//                .name(voucherTemplate.getName())
//                .discountPercentage(voucherTemplate.getDiscountPercentage())
//                .minimumPurchaseAmount(voucherTemplate.getMinimumPurchaseAmount())
//                .maximumDiscountAmount(voucherTemplate.getMaximumDiscountAmount())
//                .requiredPoints(voucherTemplate.getRequiredPoints())
//                .validityDays(voucherTemplate.getValidityDays())
//                .isActive(voucherTemplate.getIsActive())
//                .createdAt(voucherTemplate.getCreatedAt())
//                .updatedAt(voucherTemplate.getUpdatedAt())
//                .build();
//    }
}
