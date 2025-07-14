package com.boeing.bookingservice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import feign.RequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;

@Configuration
public class FeignConfig {

    private static final Logger log = LoggerFactory.getLogger(FeignConfig.class);

    @Bean
    public RequestInterceptor requestInterceptor() {
        return requestTemplate -> {
            String authHeader = null;
            
            // Try to get the authorization header from the current request
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                authHeader = request.getHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    requestTemplate.header("Authorization", authHeader);
                    log.debug("🔑 Forwarding Authorization header to {}", requestTemplate.url());
                    return;
                }
            }
            
            // Also check security context as a fallback
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getCredentials() instanceof String) {
                String token = (String) authentication.getCredentials();
                if (token != null && !token.isEmpty()) {
                    requestTemplate.header("Authorization", "Bearer " + token);
                    log.debug("🔑 Using token from SecurityContext for {}", requestTemplate.url());
                    return;
                }
            }
            
            log.warn("⚠️ No Authorization header found for inter-service call to {}", requestTemplate.url());
        };
    }
}
