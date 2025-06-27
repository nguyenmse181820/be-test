package com.boeing.bookingservice.model.enums;

public enum PaymentType {
    BOOKING_INITIAL,
    BOOKING,                // Đặt vé
    RESCHEDULE_FEE,         // Dời lịch
    CANCELLATION_PENALTY,   // Hủy ve
    REFUND                  // Hoàn tiền
}
