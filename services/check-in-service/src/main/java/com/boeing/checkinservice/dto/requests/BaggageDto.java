package com.boeing.checkinservice.dto.requests;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class BaggageDto {

    @NotNull(message = "Baggage Add Ons Id should not be null")
    private String baggageAddOnsId;

    @NotNull(message = "Weight should not be null!")
    @Min(value = 1, message = "Weight should be bigger than 1")
    @Max(value = 100, message = "Weight should be under 100")
    private Double weight;

    @NotNull(message = "Type of baggage can not be null")
    private String type;

    @NotNull(message = "Tag code should not be null")
    private String tagCode;

    private Integer passengerIndex;
}
