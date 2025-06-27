package com.boeing.bookingservice.service;

import com.boeing.bookingservice.dto.request.CreatePaymentRequest;
import com.boeing.bookingservice.dto.response.PaymentStatusDTO;

import java.util.Map;
import java.util.UUID;

public interface PaymentService {
    String createVNPayPaymentUrl(CreatePaymentRequest request, UUID userId);

    PaymentStatusDTO getPaymentStatus(String bookingReference, UUID userId);
}