package com.boeing.bookingservice.saga.command;

import java.util.UUID;

public record ValidateVoucherCommand(
        UUID sagaId,
        String voucherCode,
        UUID userId,
        Double bookingAmountBeforeDiscount
) {}