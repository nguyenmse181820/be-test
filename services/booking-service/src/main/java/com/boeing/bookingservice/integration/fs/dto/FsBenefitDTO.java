package com.boeing.bookingservice.integration.fs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FsBenefitDTO {
    private UUID id;
    private String name;
    private String description;
    private String iconURL;
}