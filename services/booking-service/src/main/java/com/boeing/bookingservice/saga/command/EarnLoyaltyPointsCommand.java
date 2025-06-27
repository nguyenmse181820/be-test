package com.boeing.bookingservice.saga.command;

import java.util.UUID;

public record EarnLoyaltyPointsCommand(
        UUID sagaId,
        UUID userId,
        Double amountSpent
) {}