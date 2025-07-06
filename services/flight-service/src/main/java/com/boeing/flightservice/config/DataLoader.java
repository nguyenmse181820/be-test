package com.boeing.flightservice.config;

import java.util.UUID;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.boeing.flightservice.dto.request.RouteCreateRequestDTO;
import com.boeing.flightservice.dto.union.AirportDTO;
import com.boeing.flightservice.dto.union.BenefitDTO;
import com.boeing.flightservice.repository.AirportRepository;
import com.boeing.flightservice.service.spec.AirportService;
import com.boeing.flightservice.service.spec.BenefitService;
import com.boeing.flightservice.service.spec.RouteService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataLoader {

    private final AirportRepository airportRepository;
    private final AirportService airportService;
    private final RouteService routeService;
    private final BenefitService benefitService;

    @Bean
    public CommandLineRunner initData() {
        return args -> {
            if (airportRepository.count() == 0) {
                log.info("Starting data initialization...");
                
                // Create airports first
                createAirports();
                
                // Create routes between airports
                createRoutes();
                
                // Create benefits for seat types
                createBenefits();
                
                log.info("Data initialization completed!");
            } else {
                log.info("Data already exists, skipping initialization.");
            }
        };
    }
    
    private void createAirports() {
        log.info("Creating airports...");
        
        // Ho Chi Minh City Airport (Tan Son Nhat)
        AirportDTO.CreateRequest hcmAirport = AirportDTO.CreateRequest.builder()
                .name("Tan Son Nhat International Airport")
                .code("SGN")
                .city("Ho Chi Minh City")
                .country("Vietnam")
                .timezone("Asia/Ho_Chi_Minh")
                .latitude(10.8187)
                .longitude(106.6520)
                .build();
        airportService.createAirport(hcmAirport);
        log.info("Created airport: SGN - Ho Chi Minh City");
        
        // Hanoi Airport (Noi Bai)
        AirportDTO.CreateRequest hanoiAirport = AirportDTO.CreateRequest.builder()
                .name("Noi Bai International Airport")
                .code("HAN")
                .city("Hanoi")
                .country("Vietnam")
                .timezone("Asia/Ho_Chi_Minh")
                .latitude(21.2212)
                .longitude(105.8072)
                .build();
        airportService.createAirport(hanoiAirport);
        log.info("Created airport: HAN - Hanoi");
        
        // Bangkok Airport (Suvarnabhumi)
        AirportDTO.CreateRequest bangkokAirport = AirportDTO.CreateRequest.builder()
                .name("Suvarnabhumi Airport")
                .code("BKK")
                .city("Bangkok")
                .country("Thailand")
                .timezone("Asia/Bangkok")
                .latitude(13.6810)
                .longitude(100.7472)
                .build();
        airportService.createAirport(bangkokAirport);
        log.info("Created airport: BKK - Bangkok");
        
        // Singapore Airport (Changi)
        AirportDTO.CreateRequest singaporeAirport = AirportDTO.CreateRequest.builder()
                .name("Singapore Changi Airport")
                .code("SIN")
                .city("Singapore")
                .country("Singapore")
                .timezone("Asia/Singapore")
                .latitude(1.3644)
                .longitude(103.9915)
                .build();
        airportService.createAirport(singaporeAirport);
        log.info("Created airport: SIN - Singapore");
        
        // Kuala Lumpur Airport (KLIA)
        AirportDTO.CreateRequest klAirport = AirportDTO.CreateRequest.builder()
                .name("Kuala Lumpur International Airport")
                .code("KUL")
                .city("Kuala Lumpur")
                .country("Malaysia")
                .timezone("Asia/Kuala_Lumpur")
                .latitude(2.7456)
                .longitude(101.7072)
                .build();
        airportService.createAirport(klAirport);
        log.info("Created airport: KUL - Kuala Lumpur");
        
        log.info("All airports created successfully!");
    }
    
    private void createRoutes() {
        log.info("Creating routes...");
        
        // We need to get airport IDs after creation to create routes
        // Since we can't get them directly from createAirport response in this structure,
        // we'll fetch them from the repository
        var airports = airportRepository.findAll();
        
        UUID hcmId = null, hanoiId = null, bangkokId = null, singaporeId = null, klId = null;
        
        for (var airport : airports) {
            switch (airport.getCode()) {
                case "SGN" -> hcmId = airport.getId();
                case "HAN" -> hanoiId = airport.getId();
                case "BKK" -> bangkokId = airport.getId();
                case "SIN" -> singaporeId = airport.getId();
                case "KUL" -> klId = airport.getId();
            }
        }
        
        // Direct routes from Ho Chi Minh City
        createRoute(hcmId, hanoiId, 130, "SGN -> HAN");
        createRoute(hcmId, bangkokId, 90, "SGN -> BKK");
        createRoute(hcmId, singaporeId, 120, "SGN -> SIN");
        createRoute(hcmId, klId, 105, "SGN -> KUL");
        
        // Direct routes from Hanoi
        createRoute(hanoiId, hcmId, 130, "HAN -> SGN");
        createRoute(hanoiId, bangkokId, 150, "HAN -> BKK");
        createRoute(hanoiId, singaporeId, 210, "HAN -> SIN");
        createRoute(hanoiId, klId, 180, "HAN -> KUL");
        
        // Connecting routes (for transit flights)
        createRoute(bangkokId, hcmId, 90, "BKK -> SGN");
        createRoute(bangkokId, hanoiId, 150, "BKK -> HAN");
        createRoute(bangkokId, singaporeId, 135, "BKK -> SIN");
        createRoute(bangkokId, klId, 90, "BKK -> KUL");
        
        createRoute(singaporeId, hcmId, 120, "SIN -> SGN");
        createRoute(singaporeId, hanoiId, 210, "SIN -> HAN");
        createRoute(singaporeId, bangkokId, 135, "SIN -> BKK");
        createRoute(singaporeId, klId, 75, "SIN -> KUL");
        
        createRoute(klId, hcmId, 105, "KUL -> SGN");
        createRoute(klId, hanoiId, 180, "KUL -> HAN");
        createRoute(klId, bangkokId, 90, "KUL -> BKK");
        createRoute(klId, singaporeId, 75, "KUL -> SIN");
        
        log.info("All routes created successfully!");
    }
    
    private void createRoute(UUID originId, UUID destinationId, Integer durationMinutes, String description) {
        try {
            RouteCreateRequestDTO routeRequest = RouteCreateRequestDTO.builder()
                    .originAirportId(originId)
                    .destinationAirportId(destinationId)
                    .estimatedDurationMinutes(durationMinutes)
                    .build();
            routeService.createRoute(routeRequest);
            log.info("Created route: {}", description);
        } catch (Exception e) {
            log.warn("Failed to create route {}: {}", description, e.getMessage());
        }
    }
    
    private void createBenefits() {
        log.info("Creating flight benefits...");
        
        // Economy Class Benefits
        createBenefit("Free Baggage Allowance", "23kg checked baggage included in economy class", "/icons/baggage.svg");
        createBenefit("In-Flight Meal", "Complimentary meal service during flight", "/icons/meal.svg");
        createBenefit("Seat Selection", "Standard seat selection available", "/icons/seat.svg");
        
        // Business Class Benefits  
        createBenefit("Priority Check-in", "Dedicated check-in counters for faster processing", "/icons/priority.svg");
        createBenefit("Lounge Access", "Access to premium airport lounges", "/icons/lounge.svg");
        createBenefit("Premium Dining", "Multi-course gourmet meal with wine selection", "/icons/dining.svg");
        createBenefit("Lie-flat Seats", "Fully reclining seats that convert to beds", "/icons/bed.svg");
        createBenefit("Priority Boarding", "Board the aircraft before general passengers", "/icons/boarding.svg");
        createBenefit("Extra Baggage", "40kg checked baggage allowance", "/icons/extra-baggage.svg");
        
        // First Class Benefits
        createBenefit("Concierge Service", "Personal assistance throughout your journey", "/icons/concierge.svg");
        createBenefit("Private Suite", "Enclosed private cabin with sliding door", "/icons/suite.svg");
        createBenefit("Chef Service", "On-demand dining prepared by onboard chef", "/icons/chef.svg");
        createBenefit("Spa Services", "In-flight massage and wellness treatments", "/icons/spa.svg");
        createBenefit("Chauffeur Service", "Ground transportation to and from airport", "/icons/car.svg");
        
        // General Premium Benefits
        createBenefit("Free WiFi", "High-speed internet access throughout flight", "/icons/wifi.svg");
        createBenefit("Entertainment System", "Personal screen with movies, TV shows, and games", "/icons/entertainment.svg");
        createBenefit("Fast Track Security", "Expedited security screening process", "/icons/security.svg");
        createBenefit("Flexible Rebooking", "Change your flight without additional fees", "/icons/calendar.svg");
        createBenefit("24/7 Support", "Round-the-clock customer service assistance", "/icons/support.svg");
        
        log.info("All benefits created successfully!");
    }
    
    private void createBenefit(String name, String description, String iconURL) {
        try {
            BenefitDTO.CreateRequest benefitRequest = BenefitDTO.CreateRequest.builder()
                    .name(name)
                    .description(description)
                    .iconURL(iconURL)
                    .build();
            benefitService.createBenefit(benefitRequest);
            log.info("Created benefit: {}", name);
        } catch (Exception e) {
            log.warn("Failed to create benefit {}: {}", name, e.getMessage());
        }
    }
}