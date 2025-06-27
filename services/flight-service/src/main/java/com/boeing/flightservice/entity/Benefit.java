package com.boeing.flightservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.UUID;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
@Table(name = "flight_benefits")
public class Benefit {

    @Id
    @GeneratedValue
    UUID id;

    @Column(name = "benefit_name")
    String name;

    @Column(name = "benefit_description")
    String description;

    @Column(name = "benefit_icon_url")
    String iconURL;

    @ManyToMany(mappedBy = "benefits")
    List<FlightFare> flightFares;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    Boolean deleted = false;
}
