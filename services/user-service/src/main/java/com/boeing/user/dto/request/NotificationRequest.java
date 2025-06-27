package com.boeing.user.dto.request;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class NotificationRequest {
    private String codeTemplate;
    private LocalDateTime checkInTime;

    private UUID boardingPassId;
}
