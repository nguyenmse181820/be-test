package com.boeing.bookingservice.service;

import com.boeing.bookingservice.dto.request.RescheduleFlightRequestDTO;
import com.boeing.bookingservice.dto.response.RescheduleFlightResponseDTO;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface RescheduleService {

    /**
     * Đổi vé máy bay cho một booking detail
     * 
     * @param bookingDetailId   ID của booking detail
     * @param rescheduleRequest yêu cầu đổi vé
     * @param userId            ID của user thực hiện đổi vé
     * @param clientIpAddress   IP address của client
     * @return kết quả đổi vé
     */
    RescheduleFlightResponseDTO rescheduleBookingDetail(UUID bookingDetailId,
            RescheduleFlightRequestDTO rescheduleRequest, UUID userId,
            String clientIpAddress);

    /**
     * Kiểm tra xem một booking detail có thể đổi vé hay không
     * 
     * @param bookingDetailId ID của booking detail
     * @param userId          ID của user
     * @return true nếu có thể đổi vé
     */
    boolean canReschedule(UUID bookingDetailId, UUID userId);

    /**
     * Complete reschedule after successful payment
     * 
     * @param bookingReference     Booking reference
     * @param paymentTransactionId Payment transaction ID
     * @return History ID of the completed reschedule
     */
    UUID completeRescheduleAfterPayment(String bookingReference, String paymentTransactionId);

    /**
     * Get reschedule history for a booking reference
     * 
     * @param bookingReference Booking reference
     * @param userId           User ID
     * @return List of reschedule history
     */
    List<Map<String, Object>> getRescheduleHistory(String bookingReference, UUID userId);
}
