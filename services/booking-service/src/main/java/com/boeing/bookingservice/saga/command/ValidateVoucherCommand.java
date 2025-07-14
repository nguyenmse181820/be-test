package com.boeing.bookingservice.saga.command;

import lombok.Builder;

import java.util.UUID;

@Builder
public record ValidateVoucherCommand(
        UUID sagaId,
        String voucherCode,
        UUID userId,
        Double bookingAmountBeforeDiscount
) {}