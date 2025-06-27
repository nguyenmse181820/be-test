package com.boeing.loyalty.repository;

import com.boeing.loyalty.entity.LoyaltyPointTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LoyaltyPointTransactionRepository extends JpaRepository<LoyaltyPointTransaction, UUID> {
    LoyaltyPointTransaction findBySource(String source);

    List<LoyaltyPointTransaction> findByMembershipId(UUID membershipId);
}
