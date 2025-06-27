package com.boeing.bookingservice.saga.command;

import java.util.Map;
import java.util.UUID;

public record SendNotificationCommand(
        UUID sagaId, // bookingReference
        UUID userId,
        String recipientEmail,
        String templateCode,
        Map<String, Object> templateParams
) {}