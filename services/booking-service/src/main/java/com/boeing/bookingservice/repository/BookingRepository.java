package com.boeing.bookingservice.repository;

import com.boeing.bookingservice.model.entity.Booking;
import com.boeing.bookingservice.model.enums.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID>, JpaSpecificationExecutor<Booking> {

    Optional<Booking> findByBookingReference(String bookingReference);

    Page<Booking> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<Booking> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, BookingStatus status, Pageable pageable);

    int countByUserIdAndStatus(UUID userId, BookingStatus status);

    int countByUserId(UUID userId);

    @Query("SELECT SUM(b.totalAmount) FROM Booking b WHERE b.userId = :userId AND b.status IN ('PAID', 'COMPLETED')")
    Double sumTotalAmountByUserIdAndStatusInPaidOrCompleted(@Param("userId") UUID userId);

    List<Booking> findAllByStatusAndPaymentDeadlineBefore(BookingStatus bookingStatus, LocalDateTime currentTime);

    // Admin-specific queries

    long countByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);

    long countByStatusAndCreatedAtBetween(BookingStatus status, LocalDateTime startDate, LocalDateTime endDate);

    @Query("SELECT COALESCE(SUM(b.totalAmount), 0.0) FROM Booking b WHERE b.status IN :statuses AND b.createdAt BETWEEN :startDate AND :endDate")
    Double sumTotalAmountByStatusInAndCreatedAtBetween(
            @Param("statuses") List<BookingStatus> statuses,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Additional helper methods for counting
     */
    Long countByStatus(BookingStatus status);

    Long countByCreatedAtAfter(LocalDateTime dateFrom);

    Long countByCreatedAtBefore(LocalDateTime dateTo);

    Long countByStatusAndCreatedAtAfter(BookingStatus status, LocalDateTime dateFrom);

    Long countByStatusAndCreatedAtBefore(BookingStatus status, LocalDateTime dateTo);

    /**
     * Count bookings by status (if provided) within date range
     */
    default Long countBookingsByStatusAndDateRange(BookingStatus status, LocalDateTime dateFrom,
                                                   LocalDateTime dateTo) {
        if (status != null && dateFrom != null && dateTo != null) {
            return countByStatusAndCreatedAtBetween(status, dateFrom, dateTo);
        } else if (status != null && dateFrom == null && dateTo == null) {
            return countByStatus(status);
        } else if (status == null && dateFrom != null && dateTo != null) {
            return countByCreatedAtBetween(dateFrom, dateTo);
        } else if (status != null && dateFrom != null && dateTo == null) {
            return countByStatusAndCreatedAtAfter(status, dateFrom);
        } else if (status != null && dateFrom == null && dateTo != null) {
            return countByStatusAndCreatedAtBefore(status, dateTo);
        } else if (status == null && dateFrom != null && dateTo == null) {
            return countByCreatedAtAfter(dateFrom);
        } else if (status == null && dateFrom == null && dateTo != null) {
            return countByCreatedAtBefore(dateTo);
        } else {
            return count(); // All bookings
        }
    }

    /**
     * Count paid bookings within date range
     */
    default Long countPaidBookingsByDateRange(LocalDateTime dateFrom, LocalDateTime dateTo) {
        return countBookingsByStatusAndDateRange(BookingStatus.PAID, dateFrom, dateTo);
    }

    @Query("SELECT new map(" +
            "FUNCTION('DATE', b.createdAt) as date, " +
            "COUNT(b) as bookings, " +
            "COALESCE(SUM(CASE WHEN b.status IN :statuses THEN b.totalAmount ELSE 0 END), 0) as revenue) " +
            "FROM Booking b " +
            "WHERE b.createdAt BETWEEN :startDate AND :endDate " +
            "GROUP BY FUNCTION('DATE', b.createdAt) " +
            "ORDER BY FUNCTION('DATE', b.createdAt)")
    List<Map<String, Object>> getDailyRevenueBetween(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("statuses") List<BookingStatus> statuses);

    @Query("SELECT new map(" +
            "CONCAT(bd.originAirportCode, ' -> ', bd.destinationAirportCode) as route, " +
            "COUNT(b) as bookings, " +
            "COALESCE(SUM(CASE WHEN b.status IN :statuses THEN b.totalAmount ELSE 0 END), 0) as revenue) " +
            "FROM Booking b " +
            "JOIN b.bookingDetails bd " +
            "WHERE b.createdAt BETWEEN :startDate AND :endDate " +
            "AND b.status IN :statuses " +
            "GROUP BY bd.originAirportCode, bd.destinationAirportCode " +
            "ORDER BY COUNT(b) DESC " +
            "FETCH FIRST :limit ROWS ONLY")
    List<Map<String, Object>> getTopRoutesBetween(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("statuses") List<BookingStatus> statuses,
            @Param("limit") int limit);

    List<Booking> findByBookingDateBetween(
            LocalDate bookingDate, LocalDate bookingDate2
    );

    List<Booking> findTop10ByOrderByBookingDateDesc();
}