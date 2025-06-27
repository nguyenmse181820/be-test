package com.boeing.bookingservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBookingRequestDTO {
    @NotNull(message = "Flight ID cannot be null")
    private UUID flightId;

    @NotBlank(message = "Selected fare name cannot be blank")
    private String selectedFareName;

    @NotEmpty(message = "Passenger list cannot be empty")
    @Size(min = 1, max = 10, message = "Number of passengers must be between 1 and 10")
    @Valid
    private List<PassengerInfoDTO> passengers;

    // Seat selections for passengers (optional - if not provided, seats will be auto-assigned)
    @Valid
    private List<SeatSelectionDTO> seatSelections;

    private String voucherCode;

    @NotBlank(message = "Payment method cannot be blank")
    private String paymentMethod;
}