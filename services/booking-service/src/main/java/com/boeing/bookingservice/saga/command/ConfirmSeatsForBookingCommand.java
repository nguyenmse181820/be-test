package com.boeing.bookingservice.saga.command;

import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record ConfirmSeatsForBookingCommand(
        UUID sagaId,
        UUID flightId,
        String bookingReference,
        List<String> seatCodes,
        int passengerCount
) {}
