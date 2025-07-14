package com.boeing.checkinservice.entity;

import com.boeing.checkinservice.common.Auditable;
import com.boeing.checkinservice.entity.enums.BaggageStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.GenericGenerator;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@Table(name = "baggage")
public class Baggage extends Auditable {

    @Id
    @Column(updatable = false, nullable = false)
    UUID id;

    @Column(name = "weight")
    Double weight;

    @Column(name = "tagcode")
    String tagCode;

    @Column(name = "status")
    BaggageStatus status;

    @ManyToOne
    @JoinColumn(name = "boarding_id")
    BoardingPass boardingPass;
}
