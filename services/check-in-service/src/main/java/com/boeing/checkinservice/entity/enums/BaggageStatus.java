package com.boeing.checkinservice.entity.enums;

public enum BaggageStatus {
    CREATED,
    TAGGED,         // Đã gắn thẻ
    LOADED,         // Đã chất lên máy bay
    IN_TRANSIT,
    ARRIVED,
    CLAIMED,        // Hành khách đã nhận
    LOST,           // Thất lạc
    DAMAGED         // Hư hỏng
}
