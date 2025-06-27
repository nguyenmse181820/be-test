package com.boeing.user.service;

import com.boeing.user.dto.request.NotificationRequest;
import com.boeing.user.dto.response.NotificationDto;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

public interface NotificationService {

    Optional<?> sendNotification(NotificationRequest dto, HttpServletRequest request);

    Optional<?> viewAllNotifications(HttpServletRequest request);
}
