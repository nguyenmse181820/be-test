package com.boeing.bookingservice.service;

import com.boeing.bookingservice.dto.request.CreatePaymentRequest;
import com.boeing.bookingservice.dto.response.PaymentStatusDTO;
import com.boeing.bookingservice.model.entity.Payment;

import java.util.Map;
import java.util.UUID;

public interface PaymentService {
    String createVNPayPaymentUrl(CreatePaymentRequest request, UUID userId);
    
    Payment createPayment(CreatePaymentRequest request, UUID userId);

    PaymentStatusDTO getPaymentStatus(String bookingReference, UUID userId);
    
    /**
     * Complete a manual payment (e.g., bank transfer)
     */
    Payment completeManualPayment(String bookingReference, String transactionReference);
}