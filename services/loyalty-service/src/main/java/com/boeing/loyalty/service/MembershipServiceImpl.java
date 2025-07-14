package com.boeing.loyalty.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.boeing.loyalty.dto.membership.CreateMembershipRequestDTO;
import com.boeing.loyalty.dto.membership.MembershipResponseDTO;
import com.boeing.loyalty.dto.membership.PointTransactionResponseDTO;
import com.boeing.loyalty.dto.membership.UpdateMembershipRequestDTO;
import com.boeing.loyalty.entity.LoyaltyPointTransaction;
import com.boeing.loyalty.entity.Membership;
import com.boeing.loyalty.entity.enums.MembershipTier;
import com.boeing.loyalty.exception.BadRequestException;
import com.boeing.loyalty.repository.LoyaltyPointTransactionRepository;
import com.boeing.loyalty.repository.MembershipRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MembershipServiceImpl implements MembershipService {

    private final MembershipRepository membershipRepository;
    private final LoyaltyPointTransactionRepository loyaltyPointTransactionRepository;

    @Override
    @Transactional
    public MembershipResponseDTO createMembership(CreateMembershipRequestDTO request) {
        if (membershipRepository.findByUserId(request.getUserId()).isPresent()) {
            throw new BadRequestException("Membership already exists for user ID: " + request.getUserId());
        }

        Membership membership = Membership.builder()
                .userId(request.getUserId())
                .tier(MembershipTier.SILVER)
                .points(0)
                .totalEarnedPoints(0)
                .totalSpent(0.0)
                .build();

        membership = membershipRepository.save(membership);
        return mapToResponseDTO(membership);
    }

    @Override
    public MembershipResponseDTO getMembership(UUID id) {
        Membership membership = membershipRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Membership not found with ID: " + id));
        return mapToResponseDTO(membership);
    }

    @Override
    public MembershipResponseDTO getMembershipByUserId(UUID userId) {
        Membership membership = membershipRepository.findByUserId(userId).orElseThrow(() -> new BadRequestException("Membership not found for user ID: " + userId));
        return mapToResponseDTO(membership);
    }

    @Override
    public List<MembershipResponseDTO> getAllMemberships() {
        // FIX 9: Fixed N+1 query problem by fetching transactions with memberships in single query
        // The original code would execute 1 query for memberships + N queries for transactions (one per membership)
        // Now we only need to execute 2 queries total regardless of membership count
        List<Membership> memberships = membershipRepository.findAll();
        
        if (memberships.isEmpty()) {
            return List.of();
        }
        
        // Fetch all transactions for all memberships in one query
        List<UUID> membershipIds = memberships.stream().map(Membership::getId).toList();
        List<LoyaltyPointTransaction> allTransactions = loyaltyPointTransactionRepository.findByMembershipIdIn(membershipIds);
        
        // Group transactions by membership ID for efficient lookup
        var transactionsByMembership = allTransactions.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                    transaction -> transaction.getMembership().getId()
                ));
        
        return memberships.stream()
                .map(membership -> mapToResponseDTOWithTransactions(membership, 
                    transactionsByMembership.getOrDefault(membership.getId(), List.of())))
                .toList();
    }

    @Override
    @Transactional
    public MembershipResponseDTO updateMembership(UUID id, UpdateMembershipRequestDTO request) {
        Membership membership = membershipRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Membership not found with ID: " + id));

        if (request.getTier() != null) {
            membership.setTier(request.getTier());
        }
        if (request.getPoints() != null) {
            membership.setPoints(request.getPoints());
        }
        if (request.getTotalEarnedPoints() != null) {
            membership.setTotalEarnedPoints(request.getTotalEarnedPoints());
        }
        if (request.getTotalSpent() != null) {
            membership.setTotalSpent(request.getTotalSpent());
        }

        membership = membershipRepository.save(membership);
        return mapToResponseDTO(membership);
    }

    @Override
    @Transactional
    public void deleteMembership(UUID id) {
        if (!membershipRepository.existsById(id)) {
            throw new BadRequestException("Membership not found with ID: " + id);
        }
        membershipRepository.deleteById(id);
    }

    @Override
    public List<PointTransactionResponseDTO> getPointTransactions(UUID membershipId) {
        if (!membershipRepository.existsById(membershipId)) {
            throw new BadRequestException("Membership not found with ID: " + membershipId);
        }

        List<LoyaltyPointTransaction> transactions = loyaltyPointTransactionRepository.findByMembershipId(membershipId);
        return transactions.stream()
                .map(this::mapToTransactionDTO)
                .toList();
    }

    private MembershipResponseDTO mapToResponseDTO(Membership membership) {
        List<PointTransactionResponseDTO> transactions = loyaltyPointTransactionRepository
                .findByMembershipId(membership.getId())
                .stream()
                .map(this::mapToTransactionDTO)
                .toList();

        return MembershipResponseDTO.builder()
                .id(membership.getId())
                .userId(membership.getUserId())
                .tier(membership.getTier())
                .points(membership.getPoints())
                .totalEarnedPoints(membership.getTotalEarnedPoints())
                .totalSpent(membership.getTotalSpent())
                .createdAt(membership.getCreatedAt())
                .transactions(transactions)
                .build();
    }
    
    // FIX 9: Added separate mapping method that accepts pre-fetched transactions
    // This eliminates the need for individual database queries per membership
    private MembershipResponseDTO mapToResponseDTOWithTransactions(Membership membership, List<LoyaltyPointTransaction> transactions) {
        List<PointTransactionResponseDTO> transactionDTOs = transactions.stream()
                .map(this::mapToTransactionDTO)
                .toList();

        return MembershipResponseDTO.builder()
                .id(membership.getId())
                .userId(membership.getUserId())
                .tier(membership.getTier())
                .points(membership.getPoints())
                .totalEarnedPoints(membership.getTotalEarnedPoints())
                .totalSpent(membership.getTotalSpent())
                .createdAt(membership.getCreatedAt())
                .transactions(transactionDTOs)
                .build();
    }

    private PointTransactionResponseDTO mapToTransactionDTO(LoyaltyPointTransaction transaction) {
        return PointTransactionResponseDTO.builder()
                .id(transaction.getId())
                .type(transaction.getType())
                .source(transaction.getSource())
                .points(transaction.getPoints())
                .note(transaction.getNote())
                .createdAt(transaction.getCreatedAt())
                .build();
    }
}