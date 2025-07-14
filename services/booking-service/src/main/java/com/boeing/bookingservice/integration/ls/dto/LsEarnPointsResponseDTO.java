package com.boeing.bookingservice.integration.ls.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LsEarnPointsResponseDTO {
    @JsonProperty("status")
    private String status;

    @JsonProperty("pointsEarnedThisTransaction")
    private Long pointsEarnedThisTransaction;

    @JsonProperty("message")
    private String message;
}