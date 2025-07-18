package com.boeing.checkinservice.repository;

import com.boeing.checkinservice.entity.BoardingPass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface BoardingPassRepository extends JpaRepository<BoardingPass, UUID> {
    @Query("""
    SELECT COUNT(DISTINCT bp.bookingDetailId) = :#{#bookingDetailIds.size()}
    FROM BoardingPass bp
    WHERE bp.bookingDetailId IN :bookingDetailIds
""")
    Boolean existsAllByBookingDetailIds(@Param("bookingDetailIds") List<UUID> bookingDetailIds);

    BoardingPass findByBookingDetailId(UUID bookingDetailId);

//    @Query("SELECT COUNT(bp) FROM BoardingPass bp WHERE bp. = :flightId")
//    int countByFlightId(@Param("flightId") UUID flightId);
}
