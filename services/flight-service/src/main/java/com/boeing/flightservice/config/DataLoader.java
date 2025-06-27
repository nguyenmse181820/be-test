package com.boeing.flightservice.config;

import com.boeing.flightservice.entity.Airport;
import com.boeing.flightservice.entity.Benefit;
import com.boeing.flightservice.repository.AirportRepository;
import com.boeing.flightservice.repository.BenefitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataLoader {

    private final BenefitRepository benefitRepository;
    private final AirportRepository airportRepository;

    @Bean
    public CommandLineRunner initData() {
        return args -> {
            loadBenefits();
            loadAirports();
        };
    }

    private void loadBenefits() {
        if (benefitRepository.count() == 0) {
            log.info("Loading initial flight benefits data...");

            List<Benefit> benefits = Arrays.asList(
                    Benefit.builder()
                            .name("Free Checked Baggage")
                            .description("Includes one free checked bag up to 23kg")
                            .iconURL("https://picsum.photos/64")
                            .deleted(false)
                            .build(),

                    Benefit.builder()
                            .name("Priority Boarding")
                            .description("Board the aircraft before general passengers")
                            .iconURL("https://picsum.photos/64")
                            .deleted(false)
                            .build(),

                    Benefit.builder()
                            .name("Extra Legroom")
                            .description("Seats with additional legroom for comfort")
                            .iconURL("https://picsum.photos/64")
                            .deleted(false)
                            .build(),

                    Benefit.builder()
                            .name("In-Flight Wi-Fi")
                            .description("Complimentary internet access during flight")
                            .iconURL("https://picsum.photos/64")
                            .deleted(false)
                            .build(),

                    Benefit.builder()
                            .name("Seat Selection")
                            .description("Choose your preferred seat at no extra cost")
                            .iconURL("https://picsum.photos/64")
                            .deleted(false)
                            .build(),

                    Benefit.builder()
                            .name("Premium Meal")
                            .description("Enhanced dining experience with premium meals")
                            .iconURL("https://picsum.photos/64")
                            .deleted(false)
                            .build(),

                    Benefit.builder()
                            .name("Lounge Access")
                            .description("Access to airport lounges with refreshments")
                            .iconURL("https://picsum.photos/64")
                            .deleted(false)
                            .build(),

                    Benefit.builder()
                            .name("Fast Track Security")
                            .description("Expedited security screening process")
                            .iconURL("https://picsum.photos/64")
                            .deleted(false)
                            .build(),

                    Benefit.builder()
                            .name("Flexible Booking")
                            .description("Change or cancel your booking without fees")
                            .iconURL("https://picsum.photos/64")
                            .deleted(false)
                            .build(),

                    Benefit.builder()
                            .name("Extra Carry-On")
                            .description("Additional carry-on baggage allowance")
                            .iconURL("https://picsum.photos/64")
                            .deleted(false)
                            .build(),

                    Benefit.builder()
                            .name("Miles Earning")
                            .description("Earn frequent flyer miles with your booking")
                            .iconURL("https://picsum.photos/64")
                            .deleted(false)
                            .build(),

                    Benefit.builder()
                            .name("Priority Check-In")
                            .description("Dedicated check-in counters for faster service")
                            .iconURL("https://picsum.photos/64")
                            .deleted(false)
                            .build(),

                    Benefit.builder()
                            .name("Entertainment System")
                            .description("Personal entertainment system with movies and music")
                            .iconURL("https://picsum.photos/64")
                            .deleted(false)
                            .build(),

                    Benefit.builder()
                            .name("Power Outlets")
                            .description("In-seat power outlets for charging devices")
                            .iconURL("https://picsum.photos/64")
                            .deleted(false)
                            .build(),

                    Benefit.builder()
                            .name("Complimentary Drinks")
                            .description("Free beverages including alcoholic drinks")
                            .iconURL("https://picsum.photos/64")
                            .deleted(false)
                            .build(),

                    Benefit.builder()
                            .name("24/7 Customer Support")
                            .description("Round-the-clock customer service assistance")
                            .iconURL("https://picsum.photos/64")
                            .deleted(false)
                            .build(),

                    Benefit.builder()
                            .name("Travel Insurance")
                            .description("Complimentary travel insurance coverage")
                            .iconURL("https://picsum.photos/64")
                            .deleted(false)
                            .build(),

                    Benefit.builder()
                            .name("Upgrade Eligibility")
                            .description("Eligible for complimentary class upgrades")
                            .iconURL("https://picsum.photos/64")
                            .deleted(false)
                            .build(),

                    Benefit.builder()
                            .name("Express Baggage")
                            .description("Priority baggage handling and delivery")
                            .iconURL("https://picsum.photos/64")
                            .deleted(false)
                            .build(),

                    Benefit.builder()
                            .name("Amenity Kit")
                            .description("Complimentary amenity kit with travel essentials")
                            .iconURL("https://picsum.photos/64")
                            .deleted(false)
                            .build()
            );

            benefitRepository.saveAll(benefits);
            log.info("Successfully loaded {} flight benefits", benefits.size());
        } else {
            log.info("Flight benefits data already exists, skipping initialization");
        }
    }

    private void loadAirports() {
        if (airportRepository.count() == 0) {
            log.info("Loading initial airport data...");

            List<Airport> airports = Arrays.asList(
                    // North America
                    Airport.builder()
                            .name("John F. Kennedy International Airport")
                            .code("JFK")
                            .city("New York")
                            .country("United States")
                            .timezone("America/New_York")
                            .latitude(40.6413)
                            .longitude(-73.7781)
                            .deleted(false)
                            .build(),

                    Airport.builder()
                            .name("Los Angeles International Airport")
                            .code("LAX")
                            .city("Los Angeles")
                            .country("United States")
                            .timezone("America/Los_Angeles")
                            .latitude(33.9425)
                            .longitude(-118.4081)
                            .deleted(false)
                            .build(),

                    Airport.builder()
                            .name("Toronto Pearson International Airport")
                            .code("YYZ")
                            .city("Toronto")
                            .country("Canada")
                            .timezone("America/Toronto")
                            .latitude(43.6777)
                            .longitude(-79.6248)
                            .deleted(false)
                            .build(),

                    Airport.builder()
                            .name("Mexico City International Airport")
                            .code("MEX")
                            .city("Mexico City")
                            .country("Mexico")
                            .timezone("America/Mexico_City")
                            .latitude(19.4363)
                            .longitude(-99.0721)
                            .deleted(false)
                            .build(),

                    // South America
                    Airport.builder()
                            .name("São Paulo-Guarulhos International Airport")
                            .code("GRU")
                            .city("São Paulo")
                            .country("Brazil")
                            .timezone("America/Sao_Paulo")
                            .latitude(-23.4356)
                            .longitude(-46.4731)
                            .deleted(false)
                            .build(),

                    Airport.builder()
                            .name("Jorge Newbery Airfield")
                            .code("AEP")
                            .city("Buenos Aires")
                            .country("Argentina")
                            .timezone("America/Argentina/Buenos_Aires")
                            .latitude(-34.5592)
                            .longitude(-58.4156)
                            .deleted(false)
                            .build(),

                    Airport.builder()
                            .name("El Dorado International Airport")
                            .code("BOG")
                            .city("Bogotá")
                            .country("Colombia")
                            .timezone("America/Bogota")
                            .latitude(4.7016)
                            .longitude(-74.1469)
                            .deleted(false)
                            .build(),

                    // Europe
                    Airport.builder()
                            .name("Heathrow Airport")
                            .code("LHR")
                            .city("London")
                            .country("United Kingdom")
                            .timezone("Europe/London")
                            .latitude(51.4700)
                            .longitude(-0.4543)
                            .deleted(false)
                            .build(),

                    Airport.builder()
                            .name("Charles de Gaulle Airport")
                            .code("CDG")
                            .city("Paris")
                            .country("France")
                            .timezone("Europe/Paris")
                            .latitude(49.0097)
                            .longitude(2.5479)
                            .deleted(false)
                            .build(),

                    Airport.builder()
                            .name("Frankfurt Airport")
                            .code("FRA")
                            .city("Frankfurt")
                            .country("Germany")
                            .timezone("Europe/Berlin")
                            .latitude(50.0379)
                            .longitude(8.5622)
                            .deleted(false)
                            .build(),

                    Airport.builder()
                            .name("Amsterdam Airport Schiphol")
                            .code("AMS")
                            .city("Amsterdam")
                            .country("Netherlands")
                            .timezone("Europe/Amsterdam")
                            .latitude(52.3105)
                            .longitude(4.7683)
                            .deleted(false)
                            .build(),

                    // Asia
                    Airport.builder()
                            .name("Tokyo Haneda Airport")
                            .code("HND")
                            .city("Tokyo")
                            .country("Japan")
                            .timezone("Asia/Tokyo")
                            .latitude(35.5494)
                            .longitude(139.7798)
                            .deleted(false)
                            .build(),

                    Airport.builder()
                            .name("Singapore Changi Airport")
                            .code("SIN")
                            .city("Singapore")
                            .country("Singapore")
                            .timezone("Asia/Singapore")
                            .latitude(1.3644)
                            .longitude(103.9915)
                            .deleted(false)
                            .build(),

                    Airport.builder()
                            .name("Beijing Capital International Airport")
                            .code("PEK")
                            .city("Beijing")
                            .country("China")
                            .timezone("Asia/Shanghai")
                            .latitude(40.0799)
                            .longitude(116.6031)
                            .deleted(false)
                            .build(),

                    Airport.builder()
                            .name("Indira Gandhi International Airport")
                            .code("DEL")
                            .city("New Delhi")
                            .country("India")
                            .timezone("Asia/Kolkata")
                            .latitude(28.5562)
                            .longitude(77.1000)
                            .deleted(false)
                            .build(),

                    Airport.builder()
                            .name("Dubai International Airport")
                            .code("DXB")
                            .city("Dubai")
                            .country("United Arab Emirates")
                            .timezone("Asia/Dubai")
                            .latitude(25.2532)
                            .longitude(55.3657)
                            .deleted(false)
                            .build(),

                    // Africa
                    Airport.builder()
                            .name("O.R. Tambo International Airport")
                            .code("JNB")
                            .city("Johannesburg")
                            .country("South Africa")
                            .timezone("Africa/Johannesburg")
                            .latitude(-26.1367)
                            .longitude(28.2411)
                            .deleted(false)
                            .build(),

                    Airport.builder()
                            .name("Cairo International Airport")
                            .code("CAI")
                            .city("Cairo")
                            .country("Egypt")
                            .timezone("Africa/Cairo")
                            .latitude(30.1219)
                            .longitude(31.4056)
                            .deleted(false)
                            .build(),

                    Airport.builder()
                            .name("Mohammed V International Airport")
                            .code("CMN")
                            .city("Casablanca")
                            .country("Morocco")
                            .timezone("Africa/Casablanca")
                            .latitude(33.3675)
                            .longitude(-7.5898)
                            .deleted(false)
                            .build(),

                    // Oceania
                    Airport.builder()
                            .name("Sydney Kingsford Smith Airport")
                            .code("SYD")
                            .city("Sydney")
                            .country("Australia")
                            .timezone("Australia/Sydney")
                            .latitude(-33.9399)
                            .longitude(151.1753)
                            .deleted(false)
                            .build(),

                    Airport.builder()
                            .name("Auckland Airport")
                            .code("AKL")
                            .city("Auckland")
                            .country("New Zealand")
                            .timezone("Pacific/Auckland")
                            .latitude(-37.0082)
                            .longitude(174.7850)
                            .deleted(false)
                            .build(),

                    // Additional major hubs
                    Airport.builder()
                            .name("Istanbul Airport")
                            .code("IST")
                            .city("Istanbul")
                            .country("Turkey")
                            .timezone("Europe/Istanbul")
                            .latitude(41.2753)
                            .longitude(28.7519)
                            .deleted(false)
                            .build(),

                    Airport.builder()
                            .name("Hamad International Airport")
                            .code("DOH")
                            .city("Doha")
                            .country("Qatar")
                            .timezone("Asia/Qatar")
                            .latitude(25.2731)
                            .longitude(51.6083)
                            .deleted(false)
                            .build()
            );

            airportRepository.saveAll(airports);
            log.info("Successfully loaded {} airports", airports.size());
        } else {
            log.info("Airport data already exists, skipping initialization");
        }
    }
}