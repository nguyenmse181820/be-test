package com.boeing.bookingservice.model.entity;

import com.boeing.bookingservice.model.enums.BookingStatus;
import com.boeing.bookingservice.model.enums.BookingType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "booking")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Booking extends BaseEntity {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "booking_date", nullable = false)
    private LocalDate bookingDate;

    @Column(name = "total_amount", nullable = false)
    private Double totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingType type;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "booking_reference", nullable = false, unique = true)
    private String bookingReference;

    @Column(name = "payment_deadline")
    private LocalDateTime paymentDeadline;

    @Column(name = "applied_voucher_code")
    private String appliedVoucherCode;

    // Loyalty Integration Fields
    @Column(name = "voucher_code")
    private String voucherCode;
    
    @Column(name = "voucher_discount_amount")
    private Double voucherDiscountAmount;
    
    @Column(name = "points_earned")
    private Integer pointsEarned;
    
    @Column(name = "loyalty_transaction_id")
    private String loyaltyTransactionId;
    
    @Column(name = "original_amount")
    private Double originalAmount;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @Builder.Default
    private List<BookingDetail> bookingDetails = new ArrayList<>();

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @Builder.Default
    private List<Payment> payments = new ArrayList<>();

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @Builder.Default
    private List<BaggageAddon> baggageAddons = new ArrayList<>();

    // Helper method to get passenger count
    public int getPassengerCount() {
        return bookingDetails.size();
    }
}