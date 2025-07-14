package com.boeing.bookingservice.dto.response;

import com.boeing.bookingservice.model.enums.BookingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingSummaryDTO {
    // Basic booking information
    private UUID id;
    private String bookingReference;
    private LocalDate bookingDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // User information
    private UUID userId;
    private String userEmail; // For admin display
    private String userFullName; // For admin display

    // Flight and passenger information
    private List<FlightSummaryInBookingDTO> flightSummaries;
    private Integer passengerCount;

    // Financial information
    private Double totalAmount;
    private Double originalAmount; // Before discounts
    private Double voucherDiscountAmount;
    private String appliedVoucherCode;

    // Status and timing
    private BookingStatus status;
    private LocalDateTime paymentDeadline;

    // Additional metadata for admin
    private String bookingType; // SINGLE_SEGMENT, MULTI_SEGMENT
    private Integer pointsEarned;
    private String paymentMethod;
    private LocalDateTime completedAt;
}