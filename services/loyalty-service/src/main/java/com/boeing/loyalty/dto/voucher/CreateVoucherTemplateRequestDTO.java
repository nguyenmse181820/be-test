package com.boeing.loyalty.dto.voucher;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateVoucherTemplateRequestDTO {
    @NotBlank(message = "Voucher code is required")
    @Pattern(regexp = "^[A-Z0-9]+$", message = "Voucher code must contain only uppercase letters and numbers")
    private String code;

    @NotBlank(message = "Voucher name is required")
    private String name;

    @NotBlank(message = "Description is required")
    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    private String description;

    @NotNull(message = "Discount percentage is required")
    @Min(value = 1, message = "Discount percentage must be at least 1")
    @Max(value = 100, message = "Discount percentage cannot exceed 100")
    private Integer discountPercentage;

    @NotNull(message = "Minimum spend is required")
    @Min(value = 0, message = "Minimum spend cannot be negative")
    private Double minSpend;

    @NotNull(message = "Maximum discount is required")
    @Min(value = 0, message = "Maximum discount cannot be negative")
    private Double maxDiscount;

    @NotNull(message = "Start date is required")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    @NotNull(message = "Usage limit is required")
    @Min(value = 1, message = "Usage limit must be at least 1")
    private Integer usageLimit;

    @NotNull(message = "Points required is required")
    @Min(value = 1, message = "Points required must be at least 1")
    private Integer pointsRequired;

    @NotNull(message = "Status is required")
    private String status;
} 