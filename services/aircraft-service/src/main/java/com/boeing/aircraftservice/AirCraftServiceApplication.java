package com.boeing.aircraftservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

import java.util.TimeZone;

@SpringBootApplication
//@ComponentScan({"com.hsh.CommonServices", "com.example.InforMicroservice"})
@EnableDiscoveryClient
public class AirCraftServiceApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        SpringApplication.run(com.boeing.aircraftservice.AirCraftServiceApplication.class, args);
    }
}
