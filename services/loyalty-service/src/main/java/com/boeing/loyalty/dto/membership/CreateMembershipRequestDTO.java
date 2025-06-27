package com.boeing.loyalty.dto.membership;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateMembershipRequestDTO {
    @NotNull(message = "User ID is required")
    private UUID userId;
} 