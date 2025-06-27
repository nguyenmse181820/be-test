package com.boeing.bookingservice.repository;

import com.boeing.bookingservice.model.entity.Passenger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PassengerRepository extends JpaRepository<Passenger, UUID> {
    List<Passenger> findByUserId(UUID userId);

    Optional<Passenger> findByIdAndUserId(UUID id, UUID userId);
}

