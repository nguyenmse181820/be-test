package com.boeing.bookingservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentStatusDTO {
    private String bookingReference;
    private String bookingStatus;
    private String paymentStatus;
    private Double amount;
    private String transactionNo;
    private LocalDateTime paymentDate;
    private String paymentMethod;
}