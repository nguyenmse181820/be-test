package com.boeing.bookingservice.repository;

import com.boeing.bookingservice.model.entity.BookingDetail;
import com.boeing.bookingservice.model.entity.RescheduleFlightHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReScheduleFlightRepository extends JpaRepository<RescheduleFlightHistory, UUID> {
    List<RescheduleFlightHistory> findByBookingDetailOrderByCreatedAtDesc(BookingDetail bookingDetail);
}