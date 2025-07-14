package com.boeing.bookingservice.dto.response;

import com.boeing.bookingservice.model.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingInitiatedResponseDTO {
    private UUID bookingId;
    private String bookingReference;
    private String status;
    private Double totalAmount;
    private PaymentStatus paymentStatus;
    private String vnpayPaymentUrl;
    private LocalDateTime paymentDeadline;
    
    // Loyalty Integration Fields
    private Double originalAmount;
    private String appliedVoucherCode;
    private Double voucherDiscountAmount;
    private Double finalAmount;
    private Integer estimatedPointsToEarn;
    private String loyaltyMessage;
}