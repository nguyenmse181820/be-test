package com.boeing.bookingservice.model.entity;

import com.boeing.bookingservice.model.enums.BookingDetailStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "booking_detail")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingDetail extends BaseEntity {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(name = "flight_id", nullable = false)
    private UUID flightId;

    @Column(name = "flight_code", nullable = false, length = 50)
    private String flightCode;

    @Column(name = "origin_airport_code", nullable = false)
    private String originAirportCode;

    @Column(name = "destination_airport_code", nullable = false)
    private String destinationAirportCode;

    @Column(name = "departure_time", nullable = false)
    private LocalDateTime departureTime;

    @Column(name = "arrival_time", nullable = false)
    private LocalDateTime arrivalTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "passenger_id", nullable = false)
    private Passenger passenger;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingDetailStatus status;

    @Column(nullable = false)
    private Double price;

    @Column(name = "selected_fare_name", nullable = false, length = 100)
    private String selectedFareName;

    @Column(name = "selected_seat_code", length = 10)
    private String selectedSeatCode;

    @Column(name = "booking_code", nullable = false)
    private String bookingCode;

    @Column(name = "flight_index")
    private Integer flightIndex;

    @OneToMany(mappedBy = "bookingDetail", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<RescheduleFlightHistory> rescheduleHistories = new ArrayList<>();
}