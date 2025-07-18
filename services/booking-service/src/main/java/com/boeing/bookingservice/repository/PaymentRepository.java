package com.boeing.bookingservice.repository;

import com.boeing.bookingservice.model.entity.Booking;
import com.boeing.bookingservice.model.entity.Payment;
import com.boeing.bookingservice.model.enums.PaymentStatus;
import com.boeing.bookingservice.model.enums.PaymentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByVnpTxnRef(String vnpTxnRef);

    Optional<Payment> findByBooking(Booking booking);

    Optional<Payment> findByBookingAndPaymentType(Booking booking, PaymentType paymentType);

    /**
     * Calculate total revenue from successful payments within date range
     */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.status = :status AND p.createdAt BETWEEN :dateFrom AND :dateTo")
    Double calculateRevenueFromPaymentsBetween(@Param("status") PaymentStatus status,
                                               @Param("dateFrom") LocalDateTime dateFrom,
                                               @Param("dateTo") LocalDateTime dateTo);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.status = :status AND p.createdAt >= :dateFrom")
    Double calculateRevenueFromPaymentsAfter(@Param("status") PaymentStatus status,
                                             @Param("dateFrom") LocalDateTime dateFrom);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.status = :status AND p.createdAt <= :dateTo")
    Double calculateRevenueFromPaymentsBefore(@Param("status") PaymentStatus status,
                                              @Param("dateTo") LocalDateTime dateTo);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.status = :status")
    Double calculateTotalRevenueFromSuccessfulPayments(@Param("status") PaymentStatus status);

    /**
     * Calculate total revenue with flexible date filtering
     */
    default Double calculateTotalRevenueFromPayments(LocalDateTime dateFrom, LocalDateTime dateTo) {
        PaymentStatus completedStatus = PaymentStatus.COMPLETED;
        if (dateFrom != null && dateTo != null) {
            return calculateRevenueFromPaymentsBetween(completedStatus, dateFrom, dateTo);
        } else if (dateFrom != null && dateTo == null) {
            return calculateRevenueFromPaymentsAfter(completedStatus, dateFrom);
        } else if (dateFrom == null && dateTo != null) {
            return calculateRevenueFromPaymentsBefore(completedStatus, dateTo);
        } else {
            return calculateTotalRevenueFromSuccessfulPayments(completedStatus);
        }
    }

    /**
     * Get all successful payments within date range for detailed analysis
     */
    List<Payment> findByStatusAndCreatedAtBetween(PaymentStatus status, LocalDateTime dateFrom, LocalDateTime dateTo);

    List<Payment> findByStatusAndCreatedAtAfter(PaymentStatus status, LocalDateTime dateFrom);

    List<Payment> findByStatusAndCreatedAtBefore(PaymentStatus status, LocalDateTime dateTo);

    List<Payment> findByStatusOrderByCreatedAtDesc(PaymentStatus status);

    default List<Payment> findSuccessfulPaymentsByDateRange(LocalDateTime dateFrom, LocalDateTime dateTo) {
        PaymentStatus completedStatus = PaymentStatus.COMPLETED;
        if (dateFrom != null && dateTo != null) {
            return findByStatusAndCreatedAtBetween(completedStatus, dateFrom, dateTo);
        } else if (dateFrom != null && dateTo == null) {
            return findByStatusAndCreatedAtAfter(completedStatus, dateFrom);
        } else if (dateFrom == null && dateTo != null) {
            return findByStatusAndCreatedAtBefore(completedStatus, dateTo);
        } else {
            return findByStatusOrderByCreatedAtDesc(completedStatus);
        }
    }

    /**
     * Count total number of successful payments within date range
     */
    Long countByStatusAndCreatedAtBetween(PaymentStatus status, LocalDateTime dateFrom, LocalDateTime dateTo);

    Long countByStatusAndCreatedAtAfter(PaymentStatus status, LocalDateTime dateFrom);

    Long countByStatusAndCreatedAtBefore(PaymentStatus status, LocalDateTime dateTo);

    Long countByStatus(PaymentStatus status);

    default Long countSuccessfulPaymentsByDateRange(LocalDateTime dateFrom, LocalDateTime dateTo) {
        PaymentStatus completedStatus = PaymentStatus.COMPLETED;
        if (dateFrom != null && dateTo != null) {
            return countByStatusAndCreatedAtBetween(completedStatus, dateFrom, dateTo);
        } else if (dateFrom != null && dateTo == null) {
            return countByStatusAndCreatedAtAfter(completedStatus, dateFrom);
        } else if (dateFrom == null && dateTo != null) {
            return countByStatusAndCreatedAtBefore(completedStatus, dateTo);
        } else {
            return countByStatus(completedStatus);
        }
    }

    /**
     * Get daily revenue breakdown from payments - simplified approach
     */
    @Query("SELECT DATE(p.createdAt) as paymentDate, SUM(p.amount) as dailyRevenue " +
            "FROM Payment p " +
            "WHERE p.status = :status AND p.createdAt BETWEEN :dateFrom AND :dateTo " +
            "GROUP BY DATE(p.createdAt) " +
            "ORDER BY paymentDate")
    List<Object[]> getDailyRevenueFromPaymentsBetween(@Param("status") PaymentStatus status,
                                                      @Param("dateFrom") LocalDateTime dateFrom,
                                                      @Param("dateTo") LocalDateTime dateTo);

    @Query("SELECT DATE(p.createdAt) as paymentDate, SUM(p.amount) as dailyRevenue " +
            "FROM Payment p " +
            "WHERE p.status = :status " +
            "GROUP BY DATE(p.createdAt) " +
            "ORDER BY paymentDate")
    List<Object[]> getDailyRevenueFromAllSuccessfulPayments(@Param("status") PaymentStatus status);

    List<Payment> findByPaymentDateBetween(LocalDateTime startDate, LocalDateTime endDate);

    List<Payment> findTop10ByOrderByPaymentDateDesc();
}
