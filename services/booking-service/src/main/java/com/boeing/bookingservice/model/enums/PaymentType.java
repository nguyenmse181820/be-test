package com.boeing.bookingservice.model.enums;

public enum PaymentType {
    BOOKING_INITIAL,
    BOOKING,                    // Đặt vé
    RESCHEDULE_FEE,            // Dời lịch
    RESCHEDULE_ADDITIONAL,     // Phụ phí đổi vé
    CANCELLATION_PENALTY,      // Hủy ve
    REFUND,
    BAGGAGE_ADDON          // Hành lý bổ sung
}
