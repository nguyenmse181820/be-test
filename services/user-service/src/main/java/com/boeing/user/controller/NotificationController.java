package com.boeing.user.controller;

import com.boeing.user.dto.request.NotificationRequest;
import com.boeing.user.dto.response.ApiResponse;
import com.boeing.user.service.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/noti")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping
    public ResponseEntity<?> sendNotification(@RequestBody NotificationRequest dto, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.builder()
                        .data(notificationService.sendNotification(dto, request))
                        .message("Notification sent successfully")
                .build());
    }


    @GetMapping
    public ResponseEntity<?> getAllNotifications(HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.builder()
                        .message("All notifications")
                        .data(notificationService.viewAllNotifications(request))
                .build());
    }
}
