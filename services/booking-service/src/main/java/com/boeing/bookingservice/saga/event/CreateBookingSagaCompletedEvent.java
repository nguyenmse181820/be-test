package com.boeing.bookingservice.saga.event;

import java.util.UUID;

public record CreateBookingSagaCompletedEvent(
        UUID sagaId, // bookingReference
        UUID bookingId,
        UUID userId
) {}