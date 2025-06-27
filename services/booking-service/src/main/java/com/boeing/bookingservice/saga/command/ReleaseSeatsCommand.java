package com.boeing.bookingservice.saga.command;

import java.util.List;
import java.util.UUID;

public record ReleaseSeatsCommand(
        UUID sagaId, // bookingReference
        UUID flightId,
        List<String> seatCodes,
        String reason
) {}