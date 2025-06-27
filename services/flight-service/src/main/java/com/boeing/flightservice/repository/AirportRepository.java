package com.boeing.flightservice.repository;

import com.boeing.flightservice.entity.Airport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AirportRepository extends JpaRepository<Airport, UUID>, JpaSpecificationExecutor<Airport> {
    Optional<Airport> findByIdAndDeleted(UUID id, Boolean deleted);
}
