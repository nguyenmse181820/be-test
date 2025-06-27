package com.boeing.bookingservice.saga.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

@Getter
public class PaymentCompletedForBookingEvent extends ApplicationEvent {
    
    private final UUID sagaId;
    private final String bookingReference;
    private final UUID bookingId;
    private final boolean paymentSuccessful;
    private final String transactionId;

    public PaymentCompletedForBookingEvent(Object source, UUID sagaId, String bookingReference, 
                                          UUID bookingId, boolean paymentSuccessful, String transactionId) {
        super(source);
        this.sagaId = sagaId;
        this.bookingReference = bookingReference;
        this.bookingId = bookingId;
        this.paymentSuccessful = paymentSuccessful;
        this.transactionId = transactionId;
    }
}
