package com.boeing.checkinservice.dto.responses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BaggageDtoResponse {
    private UUID id;

    private Double weight;

    private String tagCode;
}
