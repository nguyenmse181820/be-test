package com.boeing.user.service;

import com.boeing.user.dto.request.NotificationRequest;
import com.boeing.user.dto.response.NotificationDto;
import com.boeing.user.entity.Notification;
import com.boeing.user.entity.NotificationTemplate;
import com.boeing.user.entity.User;
import com.boeing.user.entity.enums.TypeNoti;
import com.boeing.user.repository.NotificationRepository;
import com.boeing.user.repository.NotificationTemplateRepository;
import com.boeing.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService{

    private final NotificationRepository notificationRepository;

    private final NotificationTemplateRepository notificationTemplateRepository;

    private final JwtService jwtService;

    private final UserRepository userRepository;


    @Override
    public Optional<?> sendNotification(NotificationRequest dto, HttpServletRequest request) {
        String token = getTokenFromRequest(request);

        String userId = jwtService.extractClaim(token, claims -> claims.get("userId", String.class));
        User user = userRepository.findById(UUID.fromString(userId)).orElseThrow(() -> new RuntimeException("Not found user"));

        NotificationTemplate template = notificationTemplateRepository.findByCode(dto.getCodeTemplate()).orElseThrow(() -> new RuntimeException("not found template for code " + dto.getCodeTemplate()));

        Map<String, String> data = new HashMap<>();
        data.put("user_name", user.getUsername());
        data.put("checkin_time", parseDate(dto.getCheckInTime()));
        data.put("boarding_pass_id", dto.getBoardingPassId().toString());

        Notification notification = Notification.builder()
                .type(TypeNoti.SYSTEM.toString())
                .contentRendered(parseTemplate(template.getContent(), data))
                .sentAt(LocalDateTime.now())
                .status("sent")
                .user(user)
                .errorMessage(null)
                .build();

        NotificationDto mapToDto = this.mapToDto(notificationRepository.save(notification));

        return Optional.of(mapToDto);
    }

    @Override
    public Optional<?> viewAllNotifications(HttpServletRequest request) {
        String token = getTokenFromRequest(request);

        String userId = jwtService.extractClaim(token, claims -> claims.get("userId", String.class));

        List<Notification> notifications = notificationRepository.findAllByUserId(UUID.fromString(userId));
        List<NotificationDto> notificationDtos = notifications.stream().map(this::mapToDto).toList();

        return Optional.of(notificationDtos);
    }


    private String getTokenFromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7); // bỏ chuỗi "Bearer "
        }
        return null;
    }

    public String parseTemplate(String template, Map<String, String> values) {
        for (Map.Entry<String, String> entry : values.entrySet()) {
            template = template.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return template;
    }


    private NotificationDto mapToDto(Notification notification) {
        return NotificationDto.builder()
                .content(notification.getContentRendered())
                .sendAt(notification.getSentAt())
                .build();
    }

    private String parseDate(LocalDateTime date) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return date.format(formatter);
    }

}
