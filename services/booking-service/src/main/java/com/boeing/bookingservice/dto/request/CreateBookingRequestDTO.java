package com.boeing.bookingservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
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
    
    @NotNull(message = "User ID cannot be null")
    private UUID userId;
    
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
    // Key is the flight ID (UUID string), value is list of seat codes for each passenger
    private Map<String, List<String>> selectedSeatsByFlight;

    // Loyalty Integration Fields
    @Size(max = 20, message = "Voucher code must not exceed 20 characters")
    private String voucherCode;

    @NotBlank(message = "Payment method cannot be blank")
    private String paymentMethod;
    
    // Total amount from frontend pricing calculation
    private Double totalAmount;
    
    // Detailed seat pricing information from frontend
    private List<List<Map<String, Object>>> seatPricingByFlight;
    
    // Passenger breakdown
    private Map<String, Integer> passengerBreakdown;
    
    // Booking type
    private String bookingType;
    
    // Flight segments for multi-segment bookings
    private List<Map<String, Object>> flightSegments;
    
    // Whether this is a connecting flight
    private Boolean isConnectingFlight;
    
    // Price breakdown from frontend
    private Map<String, Object> priceBreakdown;
    
    // Baggage add-ons
    @Valid
    private List<BaggageAddonRequestDTO> baggageAddons;

    @AssertTrue(message = "Either voucher code should be provided or left empty")
    private boolean isVoucherCodeValid() {
        return voucherCode == null || !voucherCode.trim().isEmpty();
    }
}