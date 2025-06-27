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
@Table(name = "flight_fares")
public class FlightFare {

    @Id
    @GeneratedValue
    UUID id;

    @Column(name = "min_price")
    Double minPrice;

    @Column(name = "max_price")
    Double maxPrice;

    @Column(name = "flight_fare_name")
    String name;

    /**
     * Allows two special characters
     * "-": defines a range: example A1-B10
     * ",": separates ranges or single
     * Example: A1-B10,C2
     */
    @Column(name = "seat_range")
    String seatRange;

    @ManyToMany
    @JoinTable(
            name = "flight_fare_benefits",
            joinColumns = @JoinColumn(name = "fare_id"),
            inverseJoinColumns = @JoinColumn(name = "benefit_id")
    )
    List<Benefit> benefits;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    Boolean deleted = false;

    @ManyToOne
    @JoinColumn(name = "flight_id")
    Flight flight;
}
