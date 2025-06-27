package com.boeing.bookingservice.saga.command;

import java.util.UUID;

public record CancelVoucherUsageCommand(
        UUID sagaId,
        String voucherCode,
        UUID userId
) {}