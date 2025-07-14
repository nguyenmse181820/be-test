package com.boeing.bookingservice.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BaggageAddonRequestDTO {

    @NotNull(message = "Passenger index cannot be null")
    private Integer passengerIndex;

    private UUID flightId; // For multi-segment bookings, null for single segment

    @NotNull(message = "Baggage weight cannot be null")
    @Positive(message = "Baggage weight must be positive")
    private Double weight; // 20kg, 30kg, etc.

    @NotNull(message = "Baggage type cannot be null")
    private String type; // "EXTRA_BAG", "OVERWEIGHT", "PRIORITY"

    @NotNull(message = "Price cannot be null")
    @Positive(message = "Price must be positive")
    private Double price; // Calculated by frontend
}
