package com.boeing.bookingservice.dto.request;

import lombok.Data;

@Data
public class CreatePaymentRequest {
    private String bookingReference;
    private Double amount;
    private String bankCode;
    private String orderDescription;
    private String paymentMethod;
    private String clientIpAddress;
}