package com.boeing.bookingservice.saga.event;

import lombok.Builder;

import java.util.UUID;

@Builder
public record CreateBookingSagaFailedEvent(
        UUID sagaId,
        String bookingReference,
        String failedStep,
        String failureReason,
        UUID flightId,
        String fareName
) {}