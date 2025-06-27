package com.boeing.flightservice.dto.common;

import lombok.*;
import lombok.experimental.FieldDefaults;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class APIResponse {
    Integer statusCode;
    Object data;
    Object error;
}
