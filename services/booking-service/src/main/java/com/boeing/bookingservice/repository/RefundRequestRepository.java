package com.boeing.bookingservice.repository;

import com.boeing.bookingservice.model.entity.RefundRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefundRequestRepository extends JpaRepository<RefundRequest, UUID> {
    
    Optional<RefundRequest> findByRefundRequestId(String refundRequestId);
    
    List<RefundRequest> findByBookingId(UUID bookingId);
    
    Optional<RefundRequest> findByBookingIdAndStatus(UUID bookingId, String status);
    
    List<RefundRequest> findByStatus(String status);
    
    List<RefundRequest> findByBookingReference(String bookingReference);
}
