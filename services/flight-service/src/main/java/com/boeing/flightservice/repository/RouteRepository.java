package com.boeing.flightservice.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.boeing.flightservice.entity.Route;

@Repository
public interface RouteRepository extends JpaRepository<Route, UUID>, JpaSpecificationExecutor<Route> {
    Optional<Route> findByIdAndDeleted(UUID id, Boolean deleted);
    
    @Query("SELECT r FROM Route r WHERE r.origin.id = :originId AND r.destination.id = :destinationId AND r.deleted = false")
    Optional<Route> findByOriginAndDestination(@Param("originId") UUID originId, @Param("destinationId") UUID destinationId);
}
