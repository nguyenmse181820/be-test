package com.boeing.flightservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.TimeZone;

@SpringBootApplication
@EnableDiscoveryClient
@ComponentScan({
        "com.boeing.flightservice.controller",
        "com.boeing.flightservice.service",
        "com.boeing.flightservice.dto",
        "com.boeing.flightservice.repository",
        "com.boeing.flightservice.config"})
@EntityScan("com.boeing.flightservice.entity")
@EnableJpaRepositories("com.boeing.flightservice.repository")
public class FlightServiceApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        SpringApplication.run(FlightServiceApplication.class, args);
    }

}
