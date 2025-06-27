package com.boeing.bookingservice.dto.response;

import com.boeing.bookingservice.model.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingInitiatedResponseDTO {
    private String bookingReference;
    private Double totalAmount;
    private PaymentStatus paymentStatus;
    private String vnpayPaymentUrl;
    private LocalDateTime paymentDeadline;
}