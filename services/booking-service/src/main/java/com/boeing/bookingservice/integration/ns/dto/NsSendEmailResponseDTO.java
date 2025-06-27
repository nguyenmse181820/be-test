package com.boeing.bookingservice.integration.ns.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

enum NsRequestProcessingStatus {
    QUEUED,
    SENT_TO_PROVIDER,
    DELIVERED,
    VALIDATION_FAILED,
    TEMPLATE_NOT_FOUND,
    FAILED_INTERNAL_ERROR
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NsSendEmailResponseDTO {
    private UUID notificationLogId;
    private NsRequestProcessingStatus status;
    private String message;
}