package com.boeing.bookingservice.repository;

import com.boeing.bookingservice.model.entity.BaggageAddon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BaggageAddonRepository extends JpaRepository<BaggageAddon, UUID> {
    
    List<BaggageAddon> findByBookingId(UUID bookingId);
    
    List<BaggageAddon> findByBookingIdAndIsPostBooking(UUID bookingId, Boolean isPostBooking);
}
