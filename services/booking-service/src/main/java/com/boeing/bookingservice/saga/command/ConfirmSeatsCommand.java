package com.boeing.bookingservice.saga.command;

import java.util.List;
import java.util.UUID;

public record ConfirmSeatsCommand(
        UUID sagaId, // bookingReference
        UUID flightId,
        List<String> seatCodes
) {}