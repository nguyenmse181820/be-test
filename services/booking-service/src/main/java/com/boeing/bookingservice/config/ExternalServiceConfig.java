package com.boeing.bookingservice.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "external.services")
public class ExternalServiceConfig {
    
    private FlightService flightService = new FlightService();
    private LoyaltyService loyaltyService = new LoyaltyService();
    private NotificationService notificationService = new NotificationService();
    
    @Data
    public static class FlightService {
        @Value("${services.flight-service.url:http://localhost:8084/flight-service}")
        private String url;
        private String basePath = "/api/v1/fs";
    }
    
    @Data
    public static class LoyaltyService {
        @Value("${services.loyalty-service.url:http://localhost:8085}")
        private String url;
        private String name = "loyalty-service";
        private String basePath = "/api/v1";
    }
    
    @Data
    public static class NotificationService {
        @Value("${services.notification-service.url:http://localhost:8089}")
        private String url;
        private String name = "notification-service";
        private String basePath = "/api/v1/notifications";
    }
}
