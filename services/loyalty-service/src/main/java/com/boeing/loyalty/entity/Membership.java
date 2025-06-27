package com.boeing.loyalty.entity;

import com.boeing.loyalty.entity.enums.MembershipTier;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "memberships")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Membership {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    UUID id;

    UUID userId;

    MembershipTier tier;

    Integer points;

    Integer totalEarnedPoints;

    Double totalSpent;

    @OneToMany(mappedBy = "membership", cascade = CascadeType.ALL, orphanRemoval = true)
    List<UserVoucher> vouchers;

    @OneToMany(mappedBy = "membership", cascade = CascadeType.ALL, orphanRemoval = true)
    List<LoyaltyPointTransaction> transactions;

    @OneToMany(mappedBy = "membership", cascade = CascadeType.ALL, orphanRemoval = true)
    List<RewardRedemption> rewardRedemptions;

}
