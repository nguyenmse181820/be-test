package com.boeing.bookingservice.saga.event;

import lombok.Builder;

import java.util.UUID;

@Builder
public record VoucherUsageCancelledEvent(
        UUID sagaId,
        String voucherCode,
        UUID userId,
        boolean success,
        String errorMessage
) {}