package com.boeing.bookingservice.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RescheduleFlightRequestDTO {

    @NotNull(message = "New flight ID is required")
    private UUID newFlightId;

    @NotNull(message = "New fare name is required")
    private String newFareName;

    // Seat code có thể null nếu để hệ thống tự động chọn ghế
    private String newSeatCode;

    // Lý do đổi vé (tùy chọn)
    private String reason;

    // Payment method for additional payment (if required)
    private String paymentMethod;
}
