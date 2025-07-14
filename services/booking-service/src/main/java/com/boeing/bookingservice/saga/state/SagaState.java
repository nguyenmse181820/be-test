package com.boeing.bookingservice.saga.state;

import com.boeing.bookingservice.saga.SagaStep;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "saga_state")
public class SagaState {

    @Id
    @Column(name = "saga_id", updatable = false, nullable = false)
    private UUID sagaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_step", nullable = false)
    private SagaStep currentStep;

    // Changed from @Lob to avoid PostgreSQL LOB stream error
    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "retry_count", nullable = false, columnDefinition = "INT DEFAULT 0")
    @Builder.Default
    private int retryCount = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public <T> T getPayload(ObjectMapper objectMapper, Class<T> payloadType) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(payloadJson, payloadType);
        } catch (Exception e) {
//            log.error("Failed to deserialize saga payload for sagaId {}: {}", sagaId, e.getMessage());
            throw new RuntimeException("Failed to deserialize saga payload", e);
        }
    }

    public void setPayload(ObjectMapper objectMapper, Object payload) {
        if (payload == null) {
            this.payloadJson = null;
            return;
        }
        try {
            this.payloadJson = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
//            log.error("Failed to serialize saga payload for sagaId {}: {}", sagaId, e.getMessage());
            throw new RuntimeException("Failed to serialize saga payload", e);
        }
    }
}