package com.boeing.bookingservice.saga.event;

import com.boeing.bookingservice.saga.command.EarnLoyaltyPointsCommand;

import java.util.UUID;

public record LoyaltyPointsEarnedEvent(
        UUID sagaId,
        boolean success,
        long pointsEarned,
        long newTotalPoints,
        String failureReason,
        EarnLoyaltyPointsCommand originalCommand
) {}