package com.boeing.bookingservice.dto.response;

import com.boeing.bookingservice.model.enums.BaggageAddonType;
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
public class BaggageAddonResponseDTO {

    private UUID id;
    private Integer passengerIndex;
    private Double baggageWeight;
    private Double price;
    private UUID flightId;
    private BaggageAddonType type;
    private LocalDateTime purchaseTime;
    private Boolean isPostBooking;
}
