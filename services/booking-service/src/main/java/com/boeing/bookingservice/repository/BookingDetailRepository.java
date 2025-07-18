package com.boeing.bookingservice.repository;

import com.boeing.bookingservice.model.entity.Booking;
import com.boeing.bookingservice.model.entity.BookingDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface BookingDetailRepository extends JpaRepository<BookingDetail, UUID> {
    List<BookingDetail> findByBookingBookingReferenceAndBookingUserId(String bookingReference, UUID userId);
    List<BookingDetail> findByBookingIn(Collection<Booking> booking);
}