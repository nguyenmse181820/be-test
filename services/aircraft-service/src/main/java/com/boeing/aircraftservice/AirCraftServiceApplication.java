package com.boeing.aircraftservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
//@ComponentScan({"com.hsh.CommonServices", "com.example.InforMicroservice"})
@EnableDiscoveryClient
public class AirCraftServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(com.boeing.aircraftservice.AirCraftServiceApplication.class, args);

	}
}
