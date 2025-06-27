package com.boeing.bookingservice.saga.event;

import com.boeing.bookingservice.saga.command.ValidateVoucherCommand;

import java.util.UUID;
import java.io.Serializable;

public record VoucherValidatedEvent(
        UUID sagaId,
        boolean isValid,
        String voucherCode,
        Double discountAmount,
        String failureReason,
        ValidateVoucherCommand originalCommand
) implements Serializable {}