package com.boeing.flightservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${external.aircraftURL}")
    private String aircraftURL;

    @Bean
    public WebClient aircraftWebClient() {
        return WebClient.builder().baseUrl(aircraftURL).build();
    }

}
