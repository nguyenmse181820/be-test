package com.boeing.loyalty.service;

import com.boeing.loyalty.dto.membership.CreateMembershipRequestDTO;
import com.boeing.loyalty.dto.membership.MembershipResponseDTO;
import com.boeing.loyalty.dto.membership.PointTransactionResponseDTO;
import com.boeing.loyalty.dto.membership.UpdateMembershipRequestDTO;

import java.util.List;
import java.util.UUID;

public interface MembershipService {
    MembershipResponseDTO createMembership(CreateMembershipRequestDTO request);
    MembershipResponseDTO getMembership(UUID id);
    MembershipResponseDTO getMembershipByUserId(UUID userId);
    List<MembershipResponseDTO> getAllMemberships();
    MembershipResponseDTO updateMembership(UUID id, UpdateMembershipRequestDTO request);
    void deleteMembership(UUID id);
    List<PointTransactionResponseDTO> getPointTransactions(UUID membershipId);
}