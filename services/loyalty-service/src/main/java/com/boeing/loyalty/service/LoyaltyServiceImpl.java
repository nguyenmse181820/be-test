package com.boeing.loyalty.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.boeing.loyalty.dto.membership.EarnPointsRequestDTO;
import com.boeing.loyalty.dto.membership.EarnPointsResponseDTO;
import com.boeing.loyalty.dto.membership.MembershipResponseDTO;
import com.boeing.loyalty.dto.membership.PointTransactionResponseDTO;
import com.boeing.loyalty.dto.voucher.UseVoucherRequestDTO;
import com.boeing.loyalty.dto.voucher.UseVoucherResponseDTO;
import com.boeing.loyalty.dto.voucher.UserVoucherDTO;
import com.boeing.loyalty.dto.voucher.ValidateVoucherRequestDTO;
import com.boeing.loyalty.dto.voucher.ValidateVoucherResponseDTO;
import com.boeing.loyalty.entity.LoyaltyPointTransaction;
import com.boeing.loyalty.entity.Membership;
import com.boeing.loyalty.entity.UserVoucher;
import com.boeing.loyalty.entity.VoucherTemplate;
import com.boeing.loyalty.entity.enums.MembershipTier;
import com.boeing.loyalty.entity.enums.PointType;
import com.boeing.loyalty.exception.BadRequestException;
import com.boeing.loyalty.repository.LoyaltyPointTransactionRepository;
import com.boeing.loyalty.repository.MembershipRepository;
import com.boeing.loyalty.repository.RewardItemRepository;
import com.boeing.loyalty.repository.RewardRedemptionRepository;
import com.boeing.loyalty.repository.UserVoucherRepository;
import com.boeing.loyalty.repository.VoucherTemplateRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoyaltyServiceImpl implements LoyaltyService {

    private final MembershipRepository membershipRepository;
    private final UserVoucherRepository userVoucherRepository;
    private final RewardRedemptionRepository rewardRedemptionRepository;
    private final VoucherTemplateRepository voucherTemplateRepository;
    private final LoyaltyPointTransactionRepository loyaltyPointTransactionRepository;
    private final RewardItemRepository rewardItemRepository;

    // FIX 1: Replaced @Value on static fields with proper dependency injection
    // Static fields cannot be injected with @Value annotation because Spring creates instances
    // while static fields belong to the class itself, not instances
    @Value("${membership.silver.rate:0.01}")
    private float silverRate;
    @Value("${membership.gold.rate:0.015}")
    private float goldRate;
    @Value("${membership.platinum.rate:0.02}")
    private float platinumRate;
    
    // Configurable limits for amount and points validation
    @Value("${loyalty.points.calculation.max-amount:100000000}")
    private long maxAmountForPointsCalculation;
    @Value("${loyalty.points.calculation.max-points-per-transaction:1000000}")
    private int maxPointsPerTransaction;

    @Override
    @Transactional
    // FIX 3: Added @Transactional annotation to prevent partial state persistence
    // Without transaction, if saving the membership fails after saving the transaction,
    // we'll have orphaned transaction record with no corresponding membership update
    public EarnPointsResponseDTO earnPoints(EarnPointsRequestDTO requestDTO) {
        log.info("[LOYALTY_POINTS] Processing earn points request for booking: {}, user: {}, amount: {}", 
            requestDTO.getBookingReference(), requestDTO.getUserId(), requestDTO.getAmountSpent());
            
        // FIX 4: Improved duplicate validation with proper transaction handling
        if (validateAndHandleDuplicateTransaction(requestDTO.getBookingReference(), requestDTO.getUserId())) {
            throw new BadRequestException("Points already earned for booking reference: " + requestDTO.getBookingReference());
        }
        
        // FIX: Auto-create membership if it doesn't exist
        Membership membership = membershipRepository.findByUserId(requestDTO.getUserId())
            .orElseGet(() -> {
                log.info("[LOYALTY_POINTS] Membership not found for user {}. Creating new membership.", 
                    requestDTO.getUserId());
                
                Membership newMembership = Membership.builder()
                    .userId(requestDTO.getUserId())
                    .tier(MembershipTier.SILVER) // Start with Silver tier
                    .points(0)
                    .totalEarnedPoints(0)
                    .totalSpent(0.0)
                    .build();
                
                return membershipRepository.save(newMembership);
            });

        int pointsEarned = calculatePoints(membership, requestDTO.getAmountSpent());
        
        log.info("[LOYALTY_POINTS] Calculated {} points for booking: {}", pointsEarned, requestDTO.getBookingReference());
        
        // FIX 4: Added validation for edge case where calculation might return 0 or negative
        if (pointsEarned <= 0) {
            throw new BadRequestException("Invalid points calculation result: " + pointsEarned);
        }

        // FIX 4: Added automatic tier upgrade check after earning points
        int newTotalPoints = membership.getPoints() + pointsEarned;
        int newTotalEarnedPoints = membership.getTotalEarnedPoints() + pointsEarned;
        MembershipTier newTier = MembershipTier.fromPoints(newTotalEarnedPoints);
        
        log.info("[LOYALTY_POINTS] Updating membership for user {}: {} -> {} points, {} -> {} total earned, tier: {}", 
            requestDTO.getUserId(), membership.getPoints(), newTotalPoints, 
            membership.getTotalEarnedPoints(), newTotalEarnedPoints, newTier);
        
        membership.setPoints(newTotalPoints);
        membership.setTotalEarnedPoints(newTotalEarnedPoints);
        membership.setTier(newTier); // FIX 4: Automatically update tier based on total earned points
        membershipRepository.save(membership);

        // Create transaction AFTER updating membership to ensure consistency
        LoyaltyPointTransaction transaction = LoyaltyPointTransaction.builder()
                .type(PointType.EARN)
                .source(requestDTO.getBookingReference())
                .points(pointsEarned)
                .note("Earn points for transaction: " + requestDTO.getBookingReference())
                .membership(membership)
                .build();
        loyaltyPointTransactionRepository.save(transaction);
        
        log.info("[LOYALTY_POINTS] Successfully awarded {} points for booking: {}, new balance: {}", 
            pointsEarned, requestDTO.getBookingReference(), newTotalPoints);

        return EarnPointsResponseDTO.builder()
                .status("SUCCESS")
                .pointsEarnedThisTransaction((long) pointsEarned)
                .message("Points earned successfully")
                .build();
    }

    @Override
    @Transactional
    public UseVoucherResponseDTO useVoucher(String voucherCode) {
        UserVoucher voucher = userVoucherRepository.findByCode(voucherCode)
                .orElseThrow(() -> new BadRequestException("Invalid voucher code: " + voucherCode));
        
        // FIX 6: Added comprehensive voucher validation before usage
        // Check if voucher is already used
        if (voucher.getIsUsed() != null && voucher.getIsUsed()) {
            throw new BadRequestException("Voucher has already been used");
        }
        
        // FIX 6: Added expiration check that was completely missing
        // This was a critical security flaw - expired vouchers could be used indefinitely
        if (voucher.getExpiresAt() != null && voucher.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Voucher has expired");
        }
        
        // FIX 6: Added voucher template status validation
        // Inactive voucher templates should not allow voucher usage
        if (voucher.getVoucher().getStatus() != com.boeing.loyalty.entity.enums.VoucherStatus.ACTIVE) {
            throw new BadRequestException("Voucher template is no longer active");
        }
        
        // FIX 6: Added date range validation for voucher template
        LocalDateTime now = LocalDateTime.now();
        if (voucher.getVoucher().getStartDate().isAfter(now.toLocalDate()) || 
            voucher.getVoucher().getEndDate().isBefore(now.toLocalDate())) {
            throw new BadRequestException("Voucher is not valid for the current date");
        }
        
        voucher.setIsUsed(true);
        voucher.setUsedAt(LocalDateTime.now());
        userVoucherRepository.save(voucher);
        
        return UseVoucherResponseDTO.builder()
                .status("SUCCESS")
                .message("Voucher used successfully")
                .discountAmount(voucher.getDiscountAmount())
                .build();
    }

    @Override
    public List<UserVoucherDTO> getAllVoucher(UUID userId) {
        // FIX 8: Added input validation
        if (userId == null) {
            throw new BadRequestException("User ID cannot be null");
        }
        
        // FIX 8: Added additional filtering for expired vouchers
        List<UserVoucher> vouchers = userVoucherRepository.findByIsUsedFalseAndMembership_UserId(userId);
        
        // FIX 8: Filter out expired vouchers at the service layer
        List<UserVoucher> validVouchers = vouchers.stream()
                .filter(voucher -> voucher.getExpiresAt() == null || voucher.getExpiresAt().isAfter(LocalDateTime.now()))
                .filter(voucher -> voucher.getVoucher().getStatus() == com.boeing.loyalty.entity.enums.VoucherStatus.ACTIVE)
                .filter(voucher -> {
                    LocalDateTime now = LocalDateTime.now();
                    return !voucher.getVoucher().getStartDate().isAfter(now.toLocalDate()) &&
                           !voucher.getVoucher().getEndDate().isBefore(now.toLocalDate());
                })
                .toList();
        
        // FIX 8: Return empty list instead of null - follows Collection best practices
        // Returning null breaks the API contract and can cause NullPointerException in client code
        return validVouchers.stream()
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
    @Transactional
    // FIX 3: Added @Transactional annotation to prevent partial state persistence
    // Without transaction, if updating the transaction fails after updating membership,
    // we'll have inconsistent state between membership points and transaction records
    public String adjustPoints(String bookingId) {
        LoyaltyPointTransaction transaction = loyaltyPointTransactionRepository.findBySource(bookingId);
        if (transaction == null) {
            throw new BadRequestException("Transaction not found for booking ID: " + bookingId);
        }
        
        // FIX 7: Added validation to prevent duplicate cancellations
        if (transaction.getType() == PointType.CANCEL) {
            throw new BadRequestException("Points for booking ID " + bookingId + " have already been cancelled");
        }
        
        Membership membership = transaction.getMembership();
        int pointsToDeduct = transaction.getPoints();
        
        // FIX 7: Added validation to prevent negative point balances
        // Critical business rule: users cannot have negative points
        if (membership.getPoints() < pointsToDeduct) {
            throw new BadRequestException(
                String.format("Insufficient points balance. Current: %d, Required: %d", 
                    membership.getPoints(), pointsToDeduct));
        }
        
        // FIX 7: Added validation for total earned points
        if (membership.getTotalEarnedPoints() < pointsToDeduct) {
            throw new BadRequestException(
                String.format("Invalid total earned points adjustment. Current: %d, Adjustment: %d", 
                    membership.getTotalEarnedPoints(), pointsToDeduct));
        }
        
        int newPoints = membership.getPoints() - pointsToDeduct;
        int newTotalEarnedPoints = membership.getTotalEarnedPoints() - pointsToDeduct;
        
        // FIX 7: Added tier recalculation after point adjustment
        // User's tier might need to be downgraded after point cancellation
        MembershipTier newTier = MembershipTier.fromPoints(newTotalEarnedPoints);
        
        membership.setPoints(newPoints);
        membership.setTotalEarnedPoints(newTotalEarnedPoints);
        membership.setTier(newTier);
        membershipRepository.save(membership);
        
        transaction.setType(PointType.CANCEL);
        loyaltyPointTransactionRepository.save(transaction);
        
        return String.format("Points adjusted successfully for booking ID: %s. New balance: %d points, Tier: %s", 
            bookingId, newPoints, newTier);
    }

    /**
     * Utility method to detect and clean up orphaned loyalty point transactions
     * that exist in the database but weren't properly committed to membership points
     */
    @Override
    @Transactional
    public String cleanupOrphanedTransactions() {
        List<LoyaltyPointTransaction> orphanedTransactions = loyaltyPointTransactionRepository.findOrphanedTransactions("", PointType.EARN);
        
        if (orphanedTransactions.isEmpty()) {
            return "No orphaned transactions found";
        }
        
        int cleanedCount = 0;
        for (LoyaltyPointTransaction transaction : orphanedTransactions) {
            loyaltyPointTransactionRepository.delete(transaction);
            cleanedCount++;
        }
        
        return String.format("Cleaned up %d orphaned transactions", cleanedCount);
    }

    /**
     * Enhanced duplicate validation method that provides detailed logging
     */
    private boolean validateAndHandleDuplicateTransaction(String bookingReference, UUID userId) {
        LoyaltyPointTransaction existingTransaction = loyaltyPointTransactionRepository.findBySourceAndType(
            bookingReference, PointType.EARN);
            
        if (existingTransaction != null) {
            // Log the inconsistency for debugging
            log.warn("[LOYALTY_POINTS] Found existing transaction for booking: {} with transaction ID: {}", 
                bookingReference, existingTransaction.getId());
            
            // Check if the transaction was properly committed to membership
            Membership membership = membershipRepository.findByUserId(userId)
                .orElse(null);
            
            if (membership == null) {
                log.warn("[LOYALTY_POINTS] Orphaned transaction found - membership doesn't exist for user {}. Cleaning up transaction for booking: {}", 
                    userId, bookingReference);
                
                // Delete the orphaned transaction since there's no membership
                loyaltyPointTransactionRepository.delete(existingTransaction);
                return false; // Allow re-processing
            }
            
            log.info("[LOYALTY_POINTS] Membership status for user {}: points={}, totalEarned={}", 
                userId, membership.getPoints(), membership.getTotalEarnedPoints());
            
            // Calculate expected points if this transaction was properly processed
            // Check for orphaned transaction by verifying if membership was properly updated
            // This is more robust than just checking for 0 points, as users may have other transactions
            List<LoyaltyPointTransaction> allUserTransactions = loyaltyPointTransactionRepository.findByMembershipId(membership.getId());
            
            // Calculate what the total should be if all transactions were properly applied
            int expectedPoints = allUserTransactions.stream()
                .filter(t -> t.getType() == PointType.EARN)
                .mapToInt(LoyaltyPointTransaction::getPoints)
                .sum() - 
                allUserTransactions.stream()
                .filter(t -> t.getType() == PointType.CANCEL)
                .mapToInt(LoyaltyPointTransaction::getPoints)
                .sum();
                
            int expectedTotalEarned = allUserTransactions.stream()
                .filter(t -> t.getType() == PointType.EARN)
                .mapToInt(LoyaltyPointTransaction::getPoints)
                .sum();
            
            // If membership points don't match what they should be based on transactions, it's orphaned
            if (membership.getPoints() != expectedPoints || membership.getTotalEarnedPoints() != expectedTotalEarned) {
                log.warn("[LOYALTY_POINTS] Detected orphaned transaction for booking: {}. Expected points: {}, actual: {}. Expected total earned: {}, actual: {}. Cleaning up and re-processing.", 
                    bookingReference, expectedPoints, membership.getPoints(), expectedTotalEarned, membership.getTotalEarnedPoints());
                
                // Delete the orphaned transaction and allow re-processing
                loyaltyPointTransactionRepository.delete(existingTransaction);
                
                // Also fix the membership to match the remaining transactions
                List<LoyaltyPointTransaction> remainingTransactions = loyaltyPointTransactionRepository.findByMembershipId(membership.getId());
                int correctedPoints = remainingTransactions.stream()
                    .filter(t -> t.getType() == PointType.EARN)
                    .mapToInt(LoyaltyPointTransaction::getPoints)
                    .sum() - 
                    remainingTransactions.stream()
                    .filter(t -> t.getType() == PointType.CANCEL)
                    .mapToInt(LoyaltyPointTransaction::getPoints)
                    .sum();
                    
                int correctedTotalEarned = remainingTransactions.stream()
                    .filter(t -> t.getType() == PointType.EARN)
                    .mapToInt(LoyaltyPointTransaction::getPoints)
                    .sum();
                
                membership.setPoints(correctedPoints);
                membership.setTotalEarnedPoints(correctedTotalEarned);
                membership.setTier(MembershipTier.fromPoints(correctedTotalEarned));
                membershipRepository.save(membership);
                
                log.info("[LOYALTY_POINTS] Corrected membership for user {}: points={}, totalEarned={}, tier={}", 
                    userId, correctedPoints, correctedTotalEarned, membership.getTier());
                
                return false; // Indicates we can proceed with processing
            } else {
                log.error("[LOYALTY_POINTS] Duplicate transaction detected for booking: {}. Points already awarded.", 
                    bookingReference);
                return true; // Indicates duplicate exists and we should not process
            }
        }
        
        return false; // No existing transaction found
    }

    private Integer calculatePoints(Membership membership, BigDecimal amount) {
        // FIX 7: Added input validation for point calculation
        if (amount == null) {
            throw new IllegalArgumentException("Amount cannot be null for points calculation");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive for points calculation");
        }
        if (membership == null) {
            throw new IllegalArgumentException("Membership cannot be null for points calculation");
        }
        
        // FIX 7: Use totalEarnedPoints instead of current points for tier determination
        // This ensures tier is based on lifetime earnings, not current balance
        MembershipTier tier = MembershipTier.fromPoints(membership.getTotalEarnedPoints());
        
        float rate = switch (tier) {
            case GOLD -> goldRate;
            case PLATINUM -> platinumRate;
            default -> silverRate;
        };
        
        // FIX 7: Added validation for rate configuration
        if (rate <= 0) {
            throw new IllegalStateException("Invalid rate configuration for tier: " + tier + ", rate: " + rate);
        }
        
        // FIX 7: Added bounds checking for very large amounts that could cause overflow
        // Now using configurable maximum amount instead of hardcoded value
        BigDecimal maxAmount = BigDecimal.valueOf(maxAmountForPointsCalculation);
        if (amount.compareTo(maxAmount) > 0) {
            throw new BadRequestException("Amount too large for points calculation: " + amount + 
                    ". Maximum allowed: " + maxAmountForPointsCalculation);
        }
        
        BigDecimal pointsCalculated = amount.multiply(BigDecimal.valueOf(rate));
        int pointsEarned = pointsCalculated.intValue();
        
        // FIX 7: Added validation for minimum points earned
        if (pointsEarned <= 0) {
            return 1; // Minimum 1 point for any valid transaction
        }
        
        // FIX 7: Added maximum points cap per transaction to prevent abuse
        // Now using configurable maximum points per transaction
        if (pointsEarned > maxPointsPerTransaction) {
            return maxPointsPerTransaction;
        }
        
        return pointsEarned;
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

    @Override
    public ValidateVoucherResponseDTO validateVoucher(ValidateVoucherRequestDTO request) {
        try {
            // Input validation
            if (request.getVoucherCode() == null || request.getVoucherCode().trim().isEmpty()) {
                return ValidateVoucherResponseDTO.builder()
                        .valid(false)
                        .errorMessage("Voucher code is required")
                        .build();
            }
            
            if (request.getUserId() == null) {
                return ValidateVoucherResponseDTO.builder()
                        .valid(false)
                        .errorMessage("User ID is required")
                        .build();
            }
            
            if (request.getBookingAmount() == null || request.getBookingAmount() <= 0) {
                return ValidateVoucherResponseDTO.builder()
                        .valid(false)
                        .errorMessage("Valid booking amount is required")
                        .build();
            }
            
            // Find the user voucher by code and user
            UserVoucher userVoucher = userVoucherRepository.findByCodeAndMembership_UserId(
                    request.getVoucherCode(), request.getUserId())
                    .orElse(null);
            
            if (userVoucher == null) {
                return ValidateVoucherResponseDTO.builder()
                        .valid(false)
                        .errorMessage("Invalid voucher code or voucher not available for this user")
                        .build();
            }
            
            // Check if voucher is already used
            if (userVoucher.getIsUsed() != null && userVoucher.getIsUsed()) {
                return ValidateVoucherResponseDTO.builder()
                        .valid(false)
                        .errorMessage("Voucher has already been used")
                        .build();
            }
            
            // Check if voucher has expired
            if (userVoucher.getExpiresAt() != null && userVoucher.getExpiresAt().isBefore(LocalDateTime.now())) {
                return ValidateVoucherResponseDTO.builder()
                        .valid(false)
                        .errorMessage("Voucher has expired")
                        .build();
            }
            
            VoucherTemplate template = userVoucher.getVoucher();
            
            // Check if voucher template is active
            if (template.getStatus() != com.boeing.loyalty.entity.enums.VoucherStatus.ACTIVE) {
                return ValidateVoucherResponseDTO.builder()
                        .valid(false)
                        .errorMessage("Voucher template is no longer active")
                        .build();
            }
            
            // Check date range validity
            LocalDateTime now = LocalDateTime.now();
            if (template.getStartDate().isAfter(now.toLocalDate()) || 
                template.getEndDate().isBefore(now.toLocalDate())) {
                return ValidateVoucherResponseDTO.builder()
                        .valid(false)
                        .errorMessage("Voucher is not valid for the current date")
                        .build();
            }
            
            // Check minimum spend requirement
            if (request.getBookingAmount() < template.getMinSpend()) {
                return ValidateVoucherResponseDTO.builder()
                        .valid(false)
                        .errorMessage(String.format("Minimum spend of %.2f required. Current amount: %.2f", 
                                template.getMinSpend(), request.getBookingAmount()))
                        .build();
            }
            
            // Calculate actual discount amount based on percentage and max discount
            Double percentageDiscount = (request.getBookingAmount() * template.getDiscountPercentage()) / 100.0;
            Double discountAmount = Math.min(percentageDiscount, template.getMaxDiscount());
            
            return ValidateVoucherResponseDTO.builder()
                    .valid(true)
                    .discountAmount(discountAmount)
                    .maxDiscount(template.getMaxDiscount())
                    .minSpend(template.getMinSpend())
                    .voucherCode(request.getVoucherCode())
                    .discountPercentage(template.getDiscountPercentage())
                    .build();
                    
        } catch (Exception e) {
            log.error("Error validating voucher: {}", e.getMessage(), e);
            return ValidateVoucherResponseDTO.builder()
                    .valid(false)
                    .errorMessage("Voucher validation failed: " + e.getMessage())
                    .build();
        }
    }

    @Override
    @Transactional
    public UseVoucherResponseDTO useVoucherWithRequest(UseVoucherRequestDTO request) {
        try {
            // Input validation
            if (request.getVoucherCode() == null || request.getVoucherCode().trim().isEmpty()) {
                return UseVoucherResponseDTO.builder()
                        .success(false)
                        .status("Failed")
                        .errorMessage("Voucher code is required")
                        .build();
            }
            
            if (request.getUserId() == null) {
                return UseVoucherResponseDTO.builder()
                        .success(false)
                        .status("Failed")
                        .errorMessage("User ID is required")
                        .build();
            }
            
            if (request.getBookingReference() == null || request.getBookingReference().trim().isEmpty()) {
                return UseVoucherResponseDTO.builder()
                        .success(false)
                        .status("Failed")
                        .errorMessage("Booking reference is required")
                        .build();
            }
            
            // Find the user voucher by code and user
            UserVoucher userVoucher = userVoucherRepository.findByCodeAndMembership_UserId(
                    request.getVoucherCode(), request.getUserId())
                    .orElse(null);
            
            if (userVoucher == null) {
                return UseVoucherResponseDTO.builder()
                        .success(false)
                        .status("Failed")
                        .errorMessage("Invalid voucher code or voucher not available for this user")
                        .build();
            }
            
            // Check if voucher is already used
            if (userVoucher.getIsUsed() != null && userVoucher.getIsUsed()) {
                return UseVoucherResponseDTO.builder()
                        .success(false)
                        .status("Failed")
                        .errorMessage("Voucher has already been used")
                        .build();
            }
            
            // Check if voucher has expired
            if (userVoucher.getExpiresAt() != null && userVoucher.getExpiresAt().isBefore(LocalDateTime.now())) {
                return UseVoucherResponseDTO.builder()
                        .success(false)
                        .status("Failed")
                        .errorMessage("Voucher has expired")
                        .build();
            }
            
            // Mark voucher as used
            userVoucher.setIsUsed(true);
            userVoucher.setUsedAt(LocalDateTime.now());
            userVoucherRepository.save(userVoucher);
            
            log.info("Voucher {} successfully used by user {} for booking {}", 
                    request.getVoucherCode(), request.getUserId(), request.getBookingReference());
            
            return UseVoucherResponseDTO.builder()
                    .success(true)
                    .status("Success")
                    .voucherCode(request.getVoucherCode())
                    .usedAt(userVoucher.getUsedAt())
                    .build();
                    
        } catch (Exception e) {
            log.error("Error using voucher: {}", e.getMessage(), e);
            return UseVoucherResponseDTO.builder()
                    .success(false)
                    .status("Failed")
                    .errorMessage("Failed to use voucher: " + e.getMessage())
                    .build();
        }
    }

    @Override
    @Transactional
    public String cancelVoucherUsage(String voucherCode, UUID userId) {
        try {
            // Input validation
            if (voucherCode == null || voucherCode.trim().isEmpty()) {
                throw new BadRequestException("Voucher code is required");
            }
            
            if (userId == null) {
                throw new BadRequestException("User ID is required");
            }
            
            // Find the user voucher by code and user
            UserVoucher userVoucher = userVoucherRepository.findByCodeAndMembership_UserId(voucherCode, userId)
                    .orElseThrow(() -> new BadRequestException("Invalid voucher code or voucher not available for this user"));
            
            // Check if voucher is actually used (can only cancel used vouchers)
            if (userVoucher.getIsUsed() == null || !userVoucher.getIsUsed()) {
                throw new BadRequestException("Voucher is not used, cannot cancel usage");
            }
            
            // Restore voucher to unused state
            userVoucher.setIsUsed(false);
            userVoucher.setUsedAt(null);
            userVoucherRepository.save(userVoucher);
            
            log.info("Voucher usage cancelled for voucher {} by user {}", voucherCode, userId);
            
            return "Voucher usage cancelled successfully";
            
        } catch (Exception e) {
            log.error("Error cancelling voucher usage: {}", e.getMessage(), e);
            throw new BadRequestException("Failed to cancel voucher usage: " + e.getMessage());
        }
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
