package com.boeing.bookingservice.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

@Getter
public class BookingCompletedEvent extends ApplicationEvent {
    
    private final String bookingReference;
    private final UUID bookingId;
    private final UUID userId;
    private final Double totalAmount;
    private final String transactionId;

    public BookingCompletedEvent(Object source, String bookingReference, UUID bookingId, 
                                UUID userId, Double totalAmount, String transactionId) {
        super(source);
        this.bookingReference = bookingReference;
        this.bookingId = bookingId;
        this.userId = userId;
        this.totalAmount = totalAmount;
        this.transactionId = transactionId;
    }
}
