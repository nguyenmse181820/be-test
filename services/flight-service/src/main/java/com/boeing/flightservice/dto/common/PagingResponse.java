package com.boeing.flightservice.dto.common;

import lombok.*;
import lombok.experimental.FieldDefaults;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PagingResponse {
    Object options;
    Object content;
    Integer totalElements;
    Integer totalPages;
}
