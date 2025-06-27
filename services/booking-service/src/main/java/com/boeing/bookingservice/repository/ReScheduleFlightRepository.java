package com.boeing.bookingservice.repository;

import com.boeing.bookingservice.model.entity.RescheduleFlightHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReScheduleFlightRepository extends JpaRepository<RescheduleFlightHistory, UUID> {
}