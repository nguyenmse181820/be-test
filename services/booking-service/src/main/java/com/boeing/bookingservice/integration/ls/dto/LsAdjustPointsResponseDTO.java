package com.boeing.bookingservice.integration.ls.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LsAdjustPointsResponseDTO {
    @NotBlank(message = "Status cannot be blank")
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("message")
    private String message;
    
    @JsonProperty("membershipId")
    private UUID membershipId;
    
    @JsonProperty("currentTier")
    private String currentTier;
    
    @JsonProperty("transactionId")
    private UUID transactionId;
    
    // Default constructor
    public LsAdjustPointsResponseDTO() {}
    
    // Constructor
    public LsAdjustPointsResponseDTO(String status, String message, UUID membershipId, String currentTier, UUID transactionId) {
        this.status = status;
        this.message = message;
        this.membershipId = membershipId;
        this.currentTier = currentTier;
        this.transactionId = transactionId;
    }
    
    // Getters and setters
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public UUID getMembershipId() {
        return membershipId;
    }
    
    public void setMembershipId(UUID membershipId) {
        this.membershipId = membershipId;
    }
    
    public String getCurrentTier() {
        return currentTier;
    }
    
    public void setCurrentTier(String currentTier) {
        this.currentTier = currentTier;
    }
    
    public UUID getTransactionId() {
        return transactionId;
    }
    
    public void setTransactionId(UUID transactionId) {
        this.transactionId = transactionId;
    }
}