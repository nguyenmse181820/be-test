package com.boeing.bookingservice.saga.event;

import java.util.UUID;

public record CreateBookingSagaFailedEvent(
        UUID sagaId,
        String bookingReference,
        String failedStep,
        String failureReason,
        UUID flightId,
        String fareName
) {}