package com.boeing.bookingservice.dto.response;

import com.boeing.bookingservice.model.enums.BookingDetailStatus;
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
public class BookingDetailInfoDTO {
    private UUID bookingDetailId;
    private UUID flightId;
    private String flightCode;
    private String originAirportCode;
    private String destinationAirportCode;
    private LocalDateTime departureTime;
    private LocalDateTime arrivalTime;
    private PassengerSummaryDTO passenger;
    private String selectedFareName;
    private Double price;
    private BookingDetailStatus status;
    private String bookingCode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}