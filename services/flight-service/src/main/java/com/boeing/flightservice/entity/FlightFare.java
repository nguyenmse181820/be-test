package com.boeing.flightservice.entity;

import java.util.List;
import java.util.UUID;

import com.boeing.flightservice.entity.enums.FareType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

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

    @Column(name = "fare_type")
    @Enumerated(EnumType.STRING)
    FareType fareType;

    // Separated by comma
    @Column(name = "seats")
    String seats;

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

    @OneToMany(mappedBy = "flightFare")
    List<Seat> occupiedSeats;
}
