package com.boeing.aircraftservice.configurations;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    // CORS is handled by the Gateway, so we don't need to configure it here
    // This prevents duplicate CORS headers that cause browser errors
    
    // @Override
    // public void addCorsMappings(CorsRegistry registry) {
    //     registry.addMapping("/**")
    //         .allowedOrigins("http://localhost:5173", "http://localhost:3000", "http://localhost:3003")  // Specific origins instead of "*"
    //         .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
    //         .allowedHeaders("Authorization", "Content-Type", "Accept")
    //         .allowCredentials(true)
    //         .maxAge(3600);
    // }
}
