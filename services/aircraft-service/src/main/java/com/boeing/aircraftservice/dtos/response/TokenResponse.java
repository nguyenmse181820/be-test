package com.boeing.aircraftservice.dtos.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Value
@NoArgsConstructor(force = true)
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TokenResponse {
    String code;
    String message;
    String token;
    String refreshToken;
    Long userId;
    String email;
}
