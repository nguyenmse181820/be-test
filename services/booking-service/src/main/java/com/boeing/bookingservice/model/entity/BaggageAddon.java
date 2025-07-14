package com.boeing.bookingservice.model.entity;

import com.boeing.bookingservice.model.enums.BaggageAddonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "baggage_addon")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BaggageAddon extends BaseEntity {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(name = "passenger_index", nullable = false)
    private Integer passengerIndex;

    @Column(name = "baggage_weight", nullable = false)
    private Double baggageWeight; // 20kg, 30kg, etc.

    @Column(name = "price", nullable = false)
    private Double price;

    @Column(name = "flight_id")
    private UUID flightId; // For multi-segment bookings

    @Enumerated(EnumType.STRING)
    @Column(name = "addon_type", nullable = false)
    private BaggageAddonType type; // EXTRA_BAG, OVERWEIGHT, PRIORITY

    @Column(name = "purchase_time", nullable = false)
    private LocalDateTime purchaseTime;

    @Column(name = "is_post_booking", nullable = false)
    @Builder.Default
    private Boolean isPostBooking = false; // Whether added after initial booking
}
