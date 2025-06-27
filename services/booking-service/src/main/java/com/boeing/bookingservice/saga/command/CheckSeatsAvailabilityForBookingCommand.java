package com.boeing.bookingservice.saga.command;

import java.util.List;
import java.util.UUID;

public record CheckSeatsAvailabilityForBookingCommand(
        UUID sagaId,
        UUID flightId,
        List<String> seatCodes
) {}
