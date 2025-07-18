package com.boeing.bookingservice.dto.common;

import lombok.*;
import lombok.experimental.FieldDefaults;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class APIResponse {
    boolean success;
    String message;
    Object data;
}
