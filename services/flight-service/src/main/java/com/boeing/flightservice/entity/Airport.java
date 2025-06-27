package com.boeing.flightservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
@Table(name = "airports")
public class Airport {

    @Id
    @GeneratedValue
    UUID id;

    @Column(name = "airport_name")
    String name;

    @Column(name = "airport_code")
    String code;

    @Column(name = "city")
    String city;

    @Column(name = "country")
    String country;

    @Column(name = "timezone")
    String timezone;

    @Column(name = "latitude")
    Double latitude;

    @Column(name = "longitude")
    Double longitude;

    @OneToMany(mappedBy = "origin")
    List<Flight> originFlights;

    @OneToMany(mappedBy = "destination")
    List<Flight> destinationFlights;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    Boolean deleted = false;
}
