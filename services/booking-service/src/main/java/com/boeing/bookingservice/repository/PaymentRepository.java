package com.boeing.bookingservice.repository;

import com.boeing.bookingservice.model.entity.Booking;
import com.boeing.bookingservice.model.entity.Payment;
import com.boeing.bookingservice.model.enums.PaymentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByVnpTxnRef(String vnpTxnRef);
    Optional<Payment> findByBooking(Booking booking);
    Optional<Payment> findByBookingAndPaymentType(Booking booking, PaymentType paymentType);
}
