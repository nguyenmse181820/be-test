package com.boeing.aircraftservice.dtos.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Map;

@Data
@NoArgsConstructor(force = true)
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PagingResponse {
    String code;
    String message;
    int currentPage;
    int totalPages;
    int elementPerPage;
    long totalElements;
    Object data;
}
