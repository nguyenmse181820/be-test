package com.boeing.flightservice.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.boeing.flightservice.entity.FlightFare;

@Repository
public interface FlightFareRepository extends JpaRepository<FlightFare, UUID>, JpaSpecificationExecutor<FlightFare> {
    
    @Query("SELECT ff FROM FlightFare ff WHERE ff.flight.id = :flightId AND ff.name = :fareName AND ff.deleted = false")
    Optional<FlightFare> findByFlightIdAndFareNameAndDeleted(@Param("flightId") UUID flightId, @Param("fareName") String fareName);
}
