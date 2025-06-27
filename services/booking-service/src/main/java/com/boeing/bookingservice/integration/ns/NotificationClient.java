package com.boeing.bookingservice.integration.ns;

import com.boeing.bookingservice.integration.ns.dto.NsSendEmailRequestDTO;
import com.boeing.bookingservice.integration.ns.dto.NsSendEmailResponseDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "notification-service", url = "${services.notification-service.url}", path = "/api/v1/notifications")
public interface NotificationClient {

    @PostMapping("/send-email")
    NsSendEmailResponseDTO sendEmail(
            @RequestBody NsSendEmailRequestDTO requestDTO
    );
}