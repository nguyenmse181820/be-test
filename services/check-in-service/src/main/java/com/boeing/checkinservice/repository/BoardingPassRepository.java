package com.boeing.checkinservice.repository;

import com.boeing.checkinservice.entity.BoardingPass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface BoardingPassRepository extends JpaRepository<BoardingPass, UUID> {
    @Query("""
    SELECT CASE WHEN COUNT(bp) > 0 THEN TRUE ELSE FALSE END
    FROM BoardingPass bp
    WHERE bp.bookingDetailId = :bookingDetailId
""")
    Boolean existsByBookingDetailId(@Param("bookingDetailId") UUID bookingDetailId);

    BoardingPass findByBookingDetailId(UUID bookingDetailId);

//    @Query("SELECT COUNT(bp) FROM BoardingPass bp WHERE bp. = :flightId")
//    int countByFlightId(@Param("flightId") UUID flightId);
}
