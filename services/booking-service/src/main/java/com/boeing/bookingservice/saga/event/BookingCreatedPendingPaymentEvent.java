package com.boeing.bookingservice.saga.event;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record BookingCreatedPendingPaymentEvent(
        UUID sagaId,
        UUID bookingDatabaseId,
        String bookingReferenceDisplay,
        String vnpayPaymentUrl,
        LocalDateTime paymentDeadline,
        Double totalAmount
) {}