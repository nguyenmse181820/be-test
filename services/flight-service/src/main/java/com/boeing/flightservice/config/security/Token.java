package com.boeing.flightservice.config.security;

import lombok.Builder;

@Builder
public record Token() {

    @Builder
    public record ValidationRequest(
            String token
    ) {
    }

    @Builder
    public record ValidationResponse(
            boolean valid,
            String email,
            String role
    ) {
    }
}
