package com.boeing.checkinservice.repository;

import com.boeing.checkinservice.entity.BoardingPass;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BoardingPassRepository extends JpaRepository<BoardingPass, UUID> {
    Boolean existsByBookingDetailId(UUID bookingDetailId);
}
