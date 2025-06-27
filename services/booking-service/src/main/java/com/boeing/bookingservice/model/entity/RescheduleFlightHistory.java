package com.boeing.bookingservice.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "reschedule_flight_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RescheduleFlightHistory extends BaseEntity {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_detail_id", nullable = false)
    private BookingDetail bookingDetail;

    @Column(name = "old_flight_id", nullable = false)
    private UUID oldFlightId;

    @Column(name = "new_flight_id", nullable = false)
    private UUID newFlightId;

    @Column(name = "old_seat_code", nullable = false)
    private String oldSeatCode;

    @Column(name = "new_seat_code", nullable = false)
    private String newSeatCode;

    @Column(name = "old_price", nullable = false)
    private Double oldPrice;

    @Column(name = "new_price", nullable = false)
    private Double newPrice;

    @Column(name = "price_difference", nullable = false)
    private Double priceDifference;

}