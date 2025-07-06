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
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBookingRequestDTO {
    private UUID flightId;

    @NotEmpty(message = "Flight IDs list cannot be empty")
    private List<UUID> flightIds;

    @NotBlank(message = "Selected fare name cannot be blank")
    private String selectedFareName;

    @NotEmpty(message = "Passenger list cannot be empty")
    @Size(min = 1, max = 10, message = "Number of passengers must be between 1 and 10")
    @Valid
    private List<PassengerInfoDTO> passengers;

    @Valid
    private List<SeatSelectionDTO> seatSelections;
    
    // Seat selections mapped by flight segment for multi-segment bookings
    // Key is the flight index ("0", "1", etc.), value is list of seat codes for each passenger
    private Map<String, List<String>> selectedSeatsByFlight;

    private String voucherCode;

    @NotBlank(message = "Payment method cannot be blank")
    private String paymentMethod;
}