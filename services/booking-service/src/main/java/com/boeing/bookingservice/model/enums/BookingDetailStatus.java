package com.boeing.bookingservice.model.enums;

public enum
BookingDetailStatus {
    PENDING_PAYMENT,
    BOOKED,
    CHECKED_IN,
    BOARDED,
    COMPLETED,

    CANCELLATION_REQUESTED,
    CANCELLED,
    RESCHEDULE_IN_PROGRESS,
    RESCHEDULED
}
