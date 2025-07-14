package com.boeing.checkinservice.dto.responses;

import com.boeing.checkinservice.entity.enums.BookingStatus;
import com.boeing.checkinservice.entity.enums.BookingType;
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
public class BookingInfoDTO {
    private UUID id;
    private String bookingReference;
    private LocalDate bookingDate;
    private Double totalAmount;
    private BookingStatus status;
    private BookingType type;
    private UUID userId;
    private LocalDateTime paymentDeadline;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}