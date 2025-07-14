package com.boeing.bookingservice.saga.orchestrator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

public class RescheduleFlightSagaOrchestrator {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RescheduleFlightSagaPayload {
        // Original booking detail info
        private UUID bookingDetailId;
        private String bookingReference;
        private UUID userId;

        // Old flight info (to be stored before changes)
        private UUID oldFlightId;
        private String oldFlightCode;
        private LocalDateTime oldDepartureTime;
        private String oldSeatCode;
        private Double oldPrice;

        // New flight info
        private UUID newFlightId;
        private String newFlightCode;
        private LocalDateTime newDepartureTime;
        private String newSeatCode;
        private String newFareName;
        private Double newPrice;

        // Payment info
        private Double priceDifference;
        private String paymentMethod;
        private String clientIpAddress;
        private String vnpayPaymentUrl;
        private LocalDateTime paymentDeadline;

        // Confirmed seat info
        private String confirmedSeatCode;

        // Status tracking
        private boolean seatReleased;
        private boolean seatConfirmed;
        private boolean paymentCompleted;
    }
}
