package com.boeing.bookingservice.saga.event;

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
public class SeatAvailabilityCheckedEvent {
    private UUID sagaId;
    private UUID flightId;
    private List<String> requestedSeatCodes;
    private boolean allSeatsAvailable;
    private List<String> unavailableSeats;
    private String failureReason;
}
