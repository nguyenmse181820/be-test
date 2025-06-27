package com.boeing.bookingservice.saga.command;

import java.util.UUID;

public record UseVoucherCommand(
        UUID sagaId, // bookingReference
        String voucherCode,
        UUID userId
) {}