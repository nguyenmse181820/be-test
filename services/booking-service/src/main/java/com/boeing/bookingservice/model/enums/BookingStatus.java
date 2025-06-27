package com.boeing.bookingservice.model.enums;

public enum BookingStatus {
    DRAFT_SELECTION,        // Người dùng đang trong quá trình chọn chuyến bay/ghế
    PENDING_PAYMENT,        // Booking đã tạo (sau khi user xác nhận thông tin passenger), đang chờ thanh toán, có paymentDeadline
    PAID,                   // Thanh toán thành công, vé đã được xác nhận hoàn toàn với các hệ thống khác (FIS)
    PAYMENT_FAILED,         // Thanh toán thất bại (sau khi đã thử thanh toán)
    CANCELLED_NO_PAYMENT,   // Booking bị hủy do không thanh toán kịp thời (scheduler xử lý)
    CANCELLATION_REQUESTED, // User đã yêu cầu hủy, đang chờ xử lý (ví dụ: chờ admin xác nhận hoàn tiền thủ công)
    CANCELLED,              // Toàn bộ booking đã bị hủy bởi user (và đã xử lý xong, ví dụ: hoàn tiền xong hoặc không có hoàn tiền)
    PARTIALLY_CANCELLED,    // Một phần vé trong booking bị hủy, phần còn lại vẫn active/paid
    COMPLETED,              // Toàn bộ các chuyến bay trong booking đã hoàn thành
    FAILED_TO_CONFIRM_SEATS
}
