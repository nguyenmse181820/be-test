package com.boeing.bookingservice.integration.ls.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class LsUseVoucherResponseDTO {
    private boolean success;
    private String status;
    private String message;
    private String errorMessage;
    private String voucherCode;
    private LocalDateTime usedAt;
    
    // Helper method for backward compatibility
    public boolean isSuccess() {
        return success;
    }
    
    public String getErrorMessage() {
        return errorMessage != null ? errorMessage : message;
    }
}