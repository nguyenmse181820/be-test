package com.boeing.bookingservice.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatSelectionDTO {
    @NotBlank(message = "Seat code cannot be blank")
    private String seatCode;

    @NotNull(message = "Passenger index cannot be null")
    @Min(value = 0, message = "Passenger index must be >= 0")
    private Integer passengerIndex;

    @NotBlank(message = "Selected fare name cannot be blank")
    private String selectedFareName;
}