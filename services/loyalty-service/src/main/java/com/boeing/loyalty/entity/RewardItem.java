package com.boeing.loyalty.entity;

import com.boeing.loyalty.entity.enums.RewardType;
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
@Table(name = "reward_items")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RewardItem {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    UUID id;

    String name;

    String description;

    RewardType type;

    Integer requiredPoints;

    Integer availableQuantity;

    Integer maximumQuantityPerUser;

    Boolean isActive = true;

    @OneToMany(mappedBy = "rewardItem", cascade = CascadeType.ALL, orphanRemoval = true)
    List<RewardRedemption> rewardRedemptions;

}
