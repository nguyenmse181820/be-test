package com.boeing.flightservice.entity;

import com.boeing.flightservice.entity.enums.FlightStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
@Table(name = "flights")
public class Flight {

    @Id
    @GeneratedValue
    UUID id;

    @Column(name = "code")
    String code;

    @Column(name = "aircraft_id")
    UUID aircraftId;

    @Column(name = "departure_time")
    LocalDateTime departureTime;

    @Column(name = "estimated_arrival_time")
    LocalDateTime estimatedArrivalTime;

    @Column(name = "flight_duration_minutes")
    Integer flightDurationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "flight_status", nullable = false)
    FlightStatus status;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    Boolean deleted = false;

    @ManyToOne
    @JoinColumn(name = "destination_airport_id")
    Airport destination;

    @ManyToOne
    @JoinColumn(name = "origin_airport_id")
    Airport origin;

    @OneToMany(mappedBy = "flight", cascade = CascadeType.ALL)
    List<FlightFare> fares;

    @OneToMany(mappedBy = "flight", cascade = CascadeType.ALL)
    List<Seat> occupiedSeats;
}
