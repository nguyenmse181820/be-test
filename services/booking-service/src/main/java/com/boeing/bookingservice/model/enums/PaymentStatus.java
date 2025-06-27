package com.boeing.bookingservice.model.enums;

public enum PaymentStatus {
    PENDING,                // Đang chờ thanh toán (ví dụ: user được chuyển đến trang VNPAY)
    COMPLETED,              // Thanh toán thành công
    FAILED,                 // Thanh toán thất bại
    CANCELLED,              // Giao dịch thanh toán bị hủy (ví dụ: user tự hủy trên trang VNPAY)
    EXPIRED,                // Giao dịch thanh toán hết hạn (ví dụ: mã QR VNPAY)

    REFUND_PENDING,         // Yêu cầu hoàn tiền đang chờ nhân viên xử lý thủ công
    REFUNDED,               // Đã hoàn tiền thành công
    REFUND_FAILED,          // Hoàn tiền thất bại
    REFUND_PENDING_MANUAL
}
