package com.boeing.bookingservice.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

@Getter
public class PaymentFailedEvent extends ApplicationEvent {
    
    private final String bookingReference;
    private final UUID bookingId;
    private final UUID userId;
    private final String errorMessage;
    private final String paymentMethod;

    public PaymentFailedEvent(Object source, String bookingReference, UUID bookingId, 
                             UUID userId, String errorMessage, String paymentMethod) {
        super(source);
        this.bookingReference = bookingReference;
        this.bookingId = bookingId;
        this.userId = userId;
        this.errorMessage = errorMessage;
        this.paymentMethod = paymentMethod;
    }
}
