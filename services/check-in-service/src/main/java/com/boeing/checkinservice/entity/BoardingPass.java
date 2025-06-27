package com.boeing.checkinservice.entity;


import com.boeing.checkinservice.common.Auditable;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.GenericGenerator;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@Table(name = "boardingPass")
public class BoardingPass extends Auditable {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(
            name = "UUID",
            strategy = "org.hibernate.id.UUIDGenerator"
    )
    @Column(updatable = false, nullable = false)
    UUID id;

    @Column(columnDefinition = "text")
    String barcode;

    @Column(name = "boarding_time")
    LocalDateTime boardingTime;

    @Column(name = "issued_at")
    LocalDateTime issuedAt;

    @Column(name = "checkin_time")
    LocalDateTime checkinTime;

    @Column(name = "seat_code")
    String seatCode;

    @Column(name = "gate")
    String gate;

    @Column(name = "sequence_number")
    String sequenceNumber;

    @Column(name = "booking_detail_id",  nullable = false)
    UUID bookingDetailId;

    @OneToMany(mappedBy = "boardingPass", cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.DETACH}, fetch = FetchType.LAZY)
    List<Baggage> baggage = new ArrayList<>();
}
