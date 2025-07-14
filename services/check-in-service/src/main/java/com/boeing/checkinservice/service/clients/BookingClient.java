package com.boeing.checkinservice.service.clients;

import com.boeing.checkinservice.configuration.FeignClientConfig;
import com.boeing.checkinservice.dto.responses.ApiResponse;
import com.boeing.checkinservice.dto.responses.BookingFullDetailResponseDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "booking-service",
        url = "http://localhost:8082/booking-service/",
        configuration = FeignClientConfig.class)
public interface BookingClient {

    @GetMapping("api/v1/bookings/{bookingReference}")
    ApiResponse<BookingFullDetailResponseDTO> getBookingDetails(@PathVariable String bookingReference);
}