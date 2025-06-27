package com.boeing.bookingservice.dto.response;

import com.boeing.bookingservice.model.enums.BookingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingSummaryDTO {
    private String bookingReference;
    private LocalDate bookingDate;
    private List<FlightSummaryInBookingDTO> flightSummaries;
    private Double totalAmount;
    private BookingStatus status;
    private Integer passengerCount;
    private LocalDateTime paymentDeadline;
}