package com.boeing.bookingservice.dto.response;

import com.boeing.bookingservice.model.enums.PaymentMethod;
import com.boeing.bookingservice.model.enums.PaymentStatus;
import com.boeing.bookingservice.model.enums.PaymentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentInfoForBookingDetailDTO {
    private UUID paymentId;
    private Double amount;
    private PaymentType type;
    private PaymentStatus status;
    private PaymentMethod paymentMethod;
    private LocalDate paymentDate;
    private String transactionId;
    private LocalDateTime createdAt;
}