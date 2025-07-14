package com.boeing.loyalty.dto.membership;

import lombok.Getter;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;
import java.util.UUID;

@Getter
public class EarnPointsRequestDTO {
    // FIX 4: Added validation annotations to prevent invalid data
    // NotNull prevents null values that would cause NullPointerException
    // NotBlank prevents empty strings that would cause business logic errors
    // DecimalMin ensures non-negative amounts (can't earn points for negative spending)
    
    @NotNull(message = "User ID cannot be null")
    private UUID userId;
    
    @NotBlank(message = "Booking reference cannot be empty")
    private String bookingReference;
    
    @NotNull(message = "Amount spent cannot be null")
    @DecimalMin(value = "0.01", message = "Amount spent must be greater than 0")
    @Digits(integer = 10, fraction = 2, message = "Amount spent must be a valid currency amount")
    private BigDecimal amountSpent;
    
    @NotBlank(message = "Source cannot be empty")
    private String source;
}
