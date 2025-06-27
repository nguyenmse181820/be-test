package com.boeing.bookingservice.integration.fs.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FsAircraftDTO {
    private String id;
    private String code;
    private String model;
    private FsAircraftTypeDTO type;
}