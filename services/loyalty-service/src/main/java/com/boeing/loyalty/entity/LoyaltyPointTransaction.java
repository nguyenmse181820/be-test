package com.boeing.loyalty.entity;

import com.boeing.loyalty.entity.enums.PointType;
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
@Table(name = "loyalty_point_transactions")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LoyaltyPointTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    UUID id;

    PointType type;

    String source;

    Integer points;

    String note;

    LocalDateTime createdAt = LocalDateTime.now();

    @ManyToOne
    @JoinColumn(name = "membership_id", nullable = false)
    Membership membership;

}
