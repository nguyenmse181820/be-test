package com.boeing.bookingservice.saga.event;

import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record SeatsConfirmationResponseEvent(
        UUID sagaId,
        String bookingReference,
        boolean success,
        List<String> confirmedSeats,
        List<String> failedSeats,
        String failureReason,
        String message
) {}
