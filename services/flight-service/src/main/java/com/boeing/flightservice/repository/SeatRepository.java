package com.boeing.flightservice.repository;

import com.boeing.flightservice.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SeatRepository extends JpaRepository<Seat, UUID>, JpaSpecificationExecutor<Seat> {
    Optional<Seat> findBySeatCodeAndFlightIdAndDeleted(String seatCode, UUID flightId, Boolean deleted);
    List<Seat> findByFlightIdAndDeleted(UUID flightId, Boolean deleted);
}
