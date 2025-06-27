package com.boeing.bookingservice.saga.command;

import java.util.List;
import java.util.UUID;

public record CheckSeatsAvailabilityCommand(
        UUID sagaId,
        UUID flightId,
        List<String> seatCodes
) {}