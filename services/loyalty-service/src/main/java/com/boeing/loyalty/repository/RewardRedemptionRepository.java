package com.boeing.loyalty.repository;

import com.boeing.loyalty.entity.RewardRedemption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RewardRedemptionRepository extends JpaRepository<RewardRedemption, UUID> {

}
