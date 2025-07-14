package com.boeing.aircraftservice.pojos;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Table(name = "aircraft_type")
public class AircraftType extends BaseEntity {
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    UUID id;

    String model;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "seat_map", columnDefinition = "jsonb")
    Map<String, Object> seatMap;

    String manufacturer;

    Integer totalSeats;

    @OneToMany(mappedBy = "aircraftType")
    List<Aircraft> aircrafts;

}
