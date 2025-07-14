package com.boeing.loyalty.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.boeing.loyalty.entity.LoyaltyPointTransaction;
import com.boeing.loyalty.entity.enums.PointType;

@Repository
public interface LoyaltyPointTransactionRepository extends JpaRepository<LoyaltyPointTransaction, UUID> {
    LoyaltyPointTransaction findBySource(String source);
    
    // Enhanced method to find by source and type for better duplicate detection
    LoyaltyPointTransaction findBySourceAndType(String source, PointType type);

    List<LoyaltyPointTransaction> findByMembershipId(UUID membershipId);
    
    // FIX 9: Added method to fetch transactions for multiple memberships in single query
    // This prevents N+1 query problem when loading all memberships with their transactions
    List<LoyaltyPointTransaction> findByMembershipIdIn(List<UUID> membershipIds);
    
    // Method to find orphaned transactions (exist but no corresponding membership update)
    @Query("SELECT t FROM LoyaltyPointTransaction t WHERE t.source = :source AND t.type = :type AND t.membership.points = 0 AND t.membership.totalEarnedPoints = 0")
    List<LoyaltyPointTransaction> findOrphanedTransactions(@Param("source") String source, @Param("type") PointType type);
}
