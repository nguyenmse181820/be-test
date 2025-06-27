package com.boeing.bookingservice.saga.event;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class FareAvailabilityCheckedEvent {
    UUID sagaId;
    boolean fareAvailable;
    String requestedFareName;
    int requestedCount;
    Integer actualAvailableCount;
    Double pricePerPassengerForFare;
    String failureReason;
}