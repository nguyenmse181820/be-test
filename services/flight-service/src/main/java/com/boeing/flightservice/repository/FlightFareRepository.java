package com.boeing.flightservice.repository;

import com.boeing.flightservice.entity.FlightFare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface FlightFareRepository extends JpaRepository<FlightFare, UUID>, JpaSpecificationExecutor<FlightFare> {
}
