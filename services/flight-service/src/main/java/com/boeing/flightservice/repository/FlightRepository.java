package com.boeing.flightservice.repository;

import com.boeing.flightservice.entity.Airport;
import com.boeing.flightservice.entity.Flight;
import com.boeing.flightservice.entity.enums.FlightStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FlightRepository extends JpaRepository<Flight, UUID>, JpaSpecificationExecutor<Flight> {
    Optional<Flight> findByIdAndDeleted(UUID id, Boolean deleted);

    List<Flight> findByDepartureTimeGreaterThanEqualAndStatusAndDeletedAndDestinationAndOrigin(
            LocalDateTime departureTimeIsGreaterThan,
            FlightStatus status,
            Boolean deleted,
            Airport destination,
            Airport origin
    );

    // Added for connecting flights search - finds all flights departing from a specific origin
    List<Flight> findByDepartureTimeGreaterThanEqualAndStatusAndDeletedAndOrigin(
            LocalDateTime departureTimeIsGreaterThan,
            FlightStatus status,
            Boolean deleted,
            Airport origin
    );

    // Added for connecting flights search - finds flights between layover airport and final destination
    List<Flight> findByDepartureTimeBetweenAndStatusAndDeletedAndOriginAndDestination(
            LocalDateTime departureTimeStart,
            LocalDateTime departureTimeEnd,
            FlightStatus status,
            Boolean deleted,
            Airport origin,
            Airport destination
    );

    List<Flight> findByAircraftIdAndDeleted(UUID aircraftId, Boolean deleted);
    
    boolean existsByCodeAndDeleted(String code, Boolean deleted);
    
    Flight findByCodeAndDeleted(String code, Boolean deleted);
}
