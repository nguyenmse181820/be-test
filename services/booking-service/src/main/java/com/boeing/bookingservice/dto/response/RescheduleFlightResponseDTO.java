package com.boeing.bookingservice.dto.response;

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
public class RescheduleFlightResponseDTO {

    private UUID rescheduleHistoryId;
    private UUID bookingDetailId;
    private String bookingReference;

    // Thông tin chuyến bay cũ
    private UUID oldFlightId;
    private String oldFlightCode;
    private LocalDateTime oldDepartureTime;
    private String oldSeatCode;
    private Double oldPrice; // Giá đã bao gồm VAT 10%

    // Thông tin chuyến bay mới
    private UUID newFlightId;
    private String newFlightCode;
    private LocalDateTime newDepartureTime;
    private String newSeatCode;
    private Double newPrice; // Giá đã bao gồm VAT 10%

    // Thông tin giá
    private Double priceDifference; // Chênh lệch giá đã bao gồm VAT
    private String paymentStatus; // "NO_PAYMENT_NEEDED", "PAYMENT_REQUIRED", "NO_REFUND"
    private String paymentUrl; // URL thanh toán nếu cần thanh toán thêm

    private String status;
    private String message;
    private LocalDateTime processedAt;
}
