package com.boeing.bookingservice.event;

import org.springframework.context.ApplicationEvent;
import lombok.Getter;

@Getter
public class PaymentProcessedEvent extends ApplicationEvent {
    private final String bookingReference;
    private final boolean paymentSuccessful;
    private final String transactionIdOrError;

    public PaymentProcessedEvent(Object source, String bookingReference, boolean paymentSuccessful, String transactionIdOrError) {
        super(source);
        this.bookingReference = bookingReference;
        this.paymentSuccessful = paymentSuccessful;
        this.transactionIdOrError = transactionIdOrError;
    }
}