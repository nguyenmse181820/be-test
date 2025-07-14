package com.boeing.bookingservice.saga.command;

import lombok.Builder;

import java.util.UUID;

@Builder
public record CancelVoucherUsageCommand(
        UUID sagaId,
        String voucherCode,
        UUID userId,
        String reason
) {}