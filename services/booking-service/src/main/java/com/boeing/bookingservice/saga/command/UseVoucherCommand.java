package com.boeing.bookingservice.saga.command;

import lombok.Builder;

import java.util.UUID;

@Builder
public record UseVoucherCommand(
        UUID sagaId, // bookingReference
        String voucherCode,
        UUID userId
) {}