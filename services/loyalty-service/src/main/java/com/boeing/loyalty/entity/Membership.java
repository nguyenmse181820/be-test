package com.boeing.loyalty.entity;

import com.boeing.loyalty.entity.enums.MembershipTier;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
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
@EntityListeners(AuditingEntityListener.class)
public class Membership {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    // FIX 5: Changed from GenerationType.AUTO to UUID for consistency with other entities
    UUID id;

    @Column(nullable = false, unique = true)
    @NotNull(message = "User ID cannot be null")
    // FIX 5: Added unique constraint to prevent duplicate memberships for same user
    // Added nullable = false to enforce database constraint
    UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @NotNull(message = "Membership tier cannot be null")
    @Builder.Default
    // FIX 5: Added proper enum mapping and default value
    MembershipTier tier = MembershipTier.SILVER;

    @Column(nullable = false)
    @NotNull(message = "Points cannot be null")
    @Min(value = 0, message = "Points cannot be negative")
    @Builder.Default
    // FIX 5: Added validation to prevent negative points and default value
    Integer points = 0;

    @Column(nullable = false)
    @NotNull(message = "Total earned points cannot be null")
    @Min(value = 0, message = "Total earned points cannot be negative")
    @Builder.Default
    // FIX 5: Added validation to prevent negative total earned points
    Integer totalEarnedPoints = 0;

    @Column(nullable = false)
    @NotNull(message = "Total spent cannot be null")
    @Min(value = 0, message = "Total spent cannot be negative")
    @Builder.Default
    // FIX 5: Added validation to prevent negative total spent
    Double totalSpent = 0.0;

    @OneToMany(mappedBy = "membership", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    // FIX 5: Added explicit LAZY fetch to prevent N+1 query issues
    List<UserVoucher> vouchers;

    @OneToMany(mappedBy = "membership", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    // FIX 5: Added explicit LAZY fetch to prevent N+1 query issues
    List<LoyaltyPointTransaction> transactions;

    @OneToMany(mappedBy = "membership", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    // FIX 5: Added explicit LAZY fetch to prevent N+1 query issues
    List<RewardRedemption> rewardRedemptions;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    // FIX 5: Added audit fields for better tracking
    LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    // FIX 5: Added audit fields for better tracking
    LocalDateTime updatedAt;

}
