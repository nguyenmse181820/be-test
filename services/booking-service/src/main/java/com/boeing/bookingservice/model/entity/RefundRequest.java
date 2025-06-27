package com.boeing.bookingservice.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "refund_requests")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundRequest {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "refund_request_id", unique = true, nullable = false)
    private String refundRequestId;
    
    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;
    
    @Column(name = "booking_reference", nullable = false)
    private String bookingReference;
    
    @Column(name = "original_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal originalAmount;
    
    @Column(name = "refund_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal refundAmount;
    
    @Column(name = "reason", nullable = false)
    private String reason;
    
    @Column(name = "status", nullable = false)
    private String status; // PENDING, APPROVED, COMPLETED, REJECTED
    
    @Column(name = "requested_by")
    private String requestedBy; // System user or admin who requested
    
    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;
    
    @Column(name = "processed_by")
    private String processedBy; // Staff who processed the refund
    
    @Column(name = "processed_at")
    private LocalDateTime processedAt;
    
    @Column(name = "notes")
    private String notes;
    
    @Column(name = "transaction_proof_url")
    private String transactionProofUrl;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
