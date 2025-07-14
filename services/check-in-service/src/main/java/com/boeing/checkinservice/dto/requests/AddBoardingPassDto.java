package com.boeing.checkinservice.dto.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddBoardingPassDto {

    @NotBlank(message = "Seat code must not be blank")
    @Pattern(
            regexp = "^\\d{1,3}[A-Z]{1,2}$",
            message = "Seat code must be in format like 12A, 3B, 100C"
    )
    private String seatCode;

    @NotNull(message = "Flight ID must not be null")
    private UUID flightId;

    @NotNull(message = "Booking detail ID must not be null")
    private UUID booking_detail_id;

    @NotNull(message = "Departure time can not be null")
    private LocalDateTime departure_time;

    private List<BaggageDto> baggage;
}

