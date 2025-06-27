package com.boeing.bookingservice.integration.ns.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NsSendEmailRequestDTO {

    private UUID userId;

    @NotBlank(message = "Recipient email cannot be blank")
    @Email(message = "Recipient email should be valid")
    private String recipientEmail;

    @NotBlank(message = "Template code cannot be blank")
    private String templateCode;

    @NotNull(message = "Template parameters cannot be null, use empty map if no params")
    private Map<String, Object> templateParams;

    private UUID referenceId;
    private String referenceType;
}