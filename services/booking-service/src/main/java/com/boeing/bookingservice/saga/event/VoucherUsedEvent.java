package com.boeing.bookingservice.saga.event;

import com.boeing.bookingservice.saga.command.UseVoucherCommand;

import java.util.UUID;

public record VoucherUsedEvent(
        UUID sagaId,
        boolean success,
        String failureReason, // Nếu success = false
        UseVoucherCommand originalCommand
) {}