package com.boeing.bookingservice.integration.ns;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.boeing.bookingservice.config.FeignConfig;
import com.boeing.bookingservice.integration.ns.dto.NsSendEmailRequestDTO;
import com.boeing.bookingservice.integration.ns.dto.NsSendEmailResponseDTO;

@FeignClient(name = "notification-service", url = "${services.notification-service.url}", path = "/api/v1/notifications", configuration = FeignConfig.class)
public interface NotificationClient {

    @PostMapping("/send-email")
    NsSendEmailResponseDTO sendEmail(
            @RequestBody NsSendEmailRequestDTO requestDTO
    );
}