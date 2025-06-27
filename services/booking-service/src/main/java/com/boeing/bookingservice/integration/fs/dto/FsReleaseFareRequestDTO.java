package com.boeing.bookingservice.integration.fs.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FsReleaseFareRequestDTO {
    @NotNull(message = "Booking reference cannot be null")
    @NotBlank(message = "Booking reference cannot be blank")
    private String bookingReference;

    @NotNull(message = "Fare name cannot be null")
    @NotBlank(message = "Fare name cannot be blank")
    private String fareName;

    @NotNull(message = "Count to release cannot be null")
    @Min(value = 1, message = "Count to release must be at least 1")
    private Integer countToRelease;

    @NotBlank(message = "Reason for release cannot be blank")
    private String reason;
}
