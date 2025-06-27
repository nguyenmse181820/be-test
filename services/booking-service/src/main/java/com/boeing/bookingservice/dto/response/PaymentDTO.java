package com.boeing.bookingservice.dto.response;

import lombok.Builder;
import lombok.Data;

public abstract class PaymentDTO {

    @Builder
    @Data
    public static class VNPayResponse {
        private String code;
        private String message;
        private String paymentUrl;
    }
}