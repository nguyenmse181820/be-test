package com.boeing.flightservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Entity
@AllArgsConstructor
@Builder
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
@Table(name = "occupied_seats")
@NoArgsConstructor
public class Seat {

    @Id
    @GeneratedValue
    UUID id;

    @Column(name = "seat_code")
    String seatCode;

    @ManyToOne
    @JoinColumn(name = "flight_id")
    Flight flight;

    @Column(name = "booking_reference")
    String bookingReference;

    @Column(name = "price")
    Double price;

    @Builder.Default
    @Column(name = "is_deleted")
    Boolean deleted = false;
}
