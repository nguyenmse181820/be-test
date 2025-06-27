package com.boeing.bookingservice.repository;

import com.boeing.bookingservice.model.entity.Booking;
import com.boeing.bookingservice.model.enums.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {
    List<Booking> findByUserId(UUID userId);

    Optional<Booking> findByBookingReference(String bookingReference);

    Page<Booking> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<Booking> findAll(Specification<Booking> spec, Pageable pageable);

    List<Booking> findAllByStatusAndPaymentDeadlineBefore(BookingStatus bookingStatus, LocalDateTime currentTime);
}