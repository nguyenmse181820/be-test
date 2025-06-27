package com.boeing.flightservice.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
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
@Table(name = "routes")
public class Route {

    @Id
    @GeneratedValue
    UUID id;

    @ManyToOne
    @JoinColumn(name = "origin_airport_id", nullable = false)
    Airport origin;

    @ManyToOne
    @JoinColumn(name = "destination_airport_id", nullable = false)
    Airport destination;

    @Column(name = "estimated_duration_minutes", nullable = false)
    Double estimatedDurationMinutes;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    Boolean deleted = false;
}
