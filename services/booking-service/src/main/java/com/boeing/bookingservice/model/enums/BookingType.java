package com.boeing.bookingservice.model.enums;

/**
 * Booking types supported by the system:
 * - STANDARD: A regular booking with a single flight segment
 * - MULTI_SEGMENT: A booking with multiple connected flight segments
 * - RESCHEDULE: A booking created as part of a reschedule process
 */
public enum BookingType {
    STANDARD, MULTI_SEGMENT, RESCHEDULE
}
