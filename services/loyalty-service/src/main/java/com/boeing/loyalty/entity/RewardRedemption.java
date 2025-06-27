package com.boeing.loyalty.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "reward_redeem")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RewardRedemption {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    UUID id;

    LocalDateTime createdAt = LocalDateTime.now();

    @ManyToOne
    @JoinColumn(name = "reward_item_id", nullable = false)
    RewardItem rewardItem;

    @ManyToOne
    @JoinColumn(name = "membership_id", nullable = false)
    Membership membership;

}
