package com.boeing.bookingservice.config;

import com.boeing.bookingservice.integration.fs.FlightClient;
import com.boeing.bookingservice.integration.fs.dto.*;
import com.boeing.bookingservice.integration.ls.LoyaltyClient;
import com.boeing.bookingservice.integration.ls.dto.*;
import com.boeing.bookingservice.service.JwtService;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Arrays;
import java.util.List;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;
import java.util.HashMap;

@TestConfiguration
public class TestConfig {

    @Bean
    public FlightClient mockFlightClient() {
        FlightClient mockClient = Mockito.mock(FlightClient.class);

        // Mock searchFlights
        Mockito.when(mockClient.searchFlights(
                Mockito.anyString(), 
                Mockito.anyString(), 
                Mockito.any(LocalDate.class), 
                Mockito.anyInt()))
            .thenReturn(createMockFlightOptions());

        // Mock getFlightDetails
        Mockito.when(mockClient.getFlightDetails(Mockito.any(UUID.class)))
            .thenReturn(createMockFlightWithFareDetails());

        // Mock checkSeatsAvailability
        Mockito.when(mockClient.checkSeatsAvailability(
                Mockito.any(UUID.class), 
                Mockito.anyList()))
            .thenReturn(createMockSeatsAvailabilityResponse());

        // Mock confirmSeats
        Mockito.when(mockClient.confirmSeats(
                Mockito.any(UUID.class), 
                Mockito.any(FsConfirmSeatsRequestDTO.class)))
            .thenReturn(createMockConfirmSeatsResponse());

        // Mock releaseSeats
        Mockito.when(mockClient.releaseSeats(
                Mockito.any(UUID.class), 
                Mockito.any(FsReleaseSeatsRequestDTO.class)))
            .thenReturn(createMockReleaseSeatsResponse());

        return mockClient;
    }

    @Bean
    public LoyaltyClient mockLoyaltyClient() {
        LoyaltyClient mockClient = Mockito.mock(LoyaltyClient.class);

        // Mock validateVoucher
        Mockito.when(mockClient.validateVoucher(
                Mockito.anyString(), 
                Mockito.any(UUID.class), 
                Mockito.any(BigDecimal.class)))
            .thenReturn(createMockVoucherValidationResponse());

        // Mock useVoucher
        Mockito.when(mockClient.useVoucher(
                Mockito.anyString(), 
                Mockito.any(LsUseVoucherRequestDTO.class)))
            .thenReturn(createMockUseVoucherResponse());

        // Mock earnPoints
        Mockito.when(mockClient.earnPoints(Mockito.any(LsEarnPointsRequestDTO.class)))
            .thenReturn(createMockEarnPointsResponse());

        // Mock adjustPoints
        Mockito.when(mockClient.adjustPoints(Mockito.any(LsAdjustPointsRequestDTO.class)))
            .thenReturn(createMockAdjustPointsResponse());

        return mockClient;
    }

    @Bean
    @Primary
    public JwtService mockJwtService() {
        JwtService mockService = Mockito.mock(JwtService.class);

        // Configure mock responses for JwtService methods
        Mockito.when(mockService.isTokenValid(Mockito.anyString())).thenReturn(true);
        Mockito.when(mockService.extractUsername(Mockito.anyString())).thenReturn("test@example.com");

        List<GrantedAuthority> authorities = Arrays.asList(
            new SimpleGrantedAuthority("ROLE_USER")
        );
        Mockito.when(mockService.extractAuthorities(Mockito.anyString())).thenReturn(authorities);

        return mockService;
    }

    // Helper methods to create mock responses

    private List<FsFlightOptionDTO> createMockFlightOptions() {
        FsAirportSummaryDTO originAirport = FsAirportSummaryDTO.builder()
                .code("SGN")
                .name("Tan Son Nhat International Airport")
                .city("Ho Chi Minh City")
                .country("Vietnam")
                .build();

        FsAirportSummaryDTO destinationAirport = FsAirportSummaryDTO.builder()
                .code("HAN")
                .name("Noi Bai International Airport")
                .city("Hanoi")
                .country("Vietnam")
                .build();

        FsFlightOptionDTO option = FsFlightOptionDTO.builder()
                .flightId(UUID.randomUUID())
                .flightCode("VN123")
                .originAirport(originAirport)
                .destinationAirport(destinationAirport)
                .departureTime(LocalDateTime.now().plusDays(1))
                .arrivalTime(LocalDateTime.now().plusDays(1).plusHours(2))
                .flightDuration("2h 0m")
                .aircraftTypeModel("Boeing 787")
                .availableFares(Collections.emptyList())
                .build();

        return Collections.singletonList(option);
    }

    private FsFlightWithFareDetailsDTO createMockFlightWithFareDetails() {
        FsAirportSummaryDTO originAirport = FsAirportSummaryDTO.builder()
                .code("SGN")
                .name("Tan Son Nhat International Airport")
                .city("Ho Chi Minh City")
                .country("Vietnam")
                .build();

        FsAirportSummaryDTO destinationAirport = FsAirportSummaryDTO.builder()
                .code("HAN")
                .name("Noi Bai International Airport")
                .city("Hanoi")
                .country("Vietnam")
                .build();

        FsAircraftDTO aircraft = FsAircraftDTO.builder()
                .code("B787")
                .type(null)
                .build();

        FsDetailedFareDTO fare = FsDetailedFareDTO.builder()
                .flightFareId(UUID.randomUUID())
                .name("ECONOMY")
                .price(BigDecimal.valueOf(1000000))
                .seatsAvailableForFare(100)
                .baggageAllowance("20kg")
                .fareRules(null)
                .benefits(Collections.emptyList())
                .build();

        return FsFlightWithFareDetailsDTO.builder()
                .flightId(UUID.randomUUID())
                .flightCode("VN123")
                .aircraft(aircraft)
                .originAirport(originAirport)
                .destinationAirport(destinationAirport)
                .departureTime(LocalDateTime.now().plusDays(1))
                .estimatedArrivalTime(LocalDateTime.now().plusDays(1).plusHours(2))
                .actualArrivalTime(null)
                .status("SCHEDULED")
                .flightDuration("2h 0m")
                .seatAvailabilityMap(new HashMap<>())
                .availableFares(Collections.singletonList(fare))
                .build();
    }

    private FsSeatsAvailabilityResponseDTO createMockSeatsAvailabilityResponse() {
        FsSeatAvailabilityDetail seat1 = FsSeatAvailabilityDetail.builder()
                .seatCode("1A")
                .isAvailable(true)
                .reasonIfNotAvailable(null)
                .build();

        List<FsSeatAvailabilityDetail> seatDetails = Collections.singletonList(seat1);

        FsSeatsAvailabilityResponseDTO response = FsSeatsAvailabilityResponseDTO.builder()
                .seatStatuses(seatDetails)
                .allRequestedSeatsAvailable(true)
                .build();

        return response;
    }

    private FsConfirmSeatsResponseDTO createMockConfirmSeatsResponse() {
        FsConfirmSeatsResponseDTO response = new FsConfirmSeatsResponseDTO();
        response.setStatus("SUCCESS");
        response.setConfirmedSeats(Collections.singletonList("1A"));
        response.setFailedToConfirmSeats(Collections.emptyList());
        response.setMessage("All seats confirmed successfully");
        return response;
    }

    private FsReleaseSeatsResponseDTO createMockReleaseSeatsResponse() {
        FsReleaseSeatsResponseDTO response = new FsReleaseSeatsResponseDTO();
        response.setStatus("SUCCESS");
        response.setMessage("All seats released successfully");
        return response;
    }

    private LsVoucherValidationResponseDTO createMockVoucherValidationResponse() {
        LsVoucherValidationResponseDTO response = new LsVoucherValidationResponseDTO();
        response.setValid(true);
        response.setFinalDiscountAmount(BigDecimal.valueOf(100000));
        response.setVoucherName("WELCOME100K");
        response.setExpiredAt(LocalDateTime.now().plusDays(30));
        response.setFailureReason(null);
        return response;
    }

    private LsUseVoucherResponseDTO createMockUseVoucherResponse() {
        LsUseVoucherResponseDTO response = new LsUseVoucherResponseDTO();
        response.setStatus("SUCCESS");
        response.setMessage("Voucher used successfully");
        return response;
    }

    private LsEarnPointsResponseDTO createMockEarnPointsResponse() {
        LsEarnPointsResponseDTO response = new LsEarnPointsResponseDTO();
        response.setStatus("SUCCESS");
        response.setPointsEarnedThisTransaction(100L);
        response.setMessage("Points earned successfully");
        return response;
    }

    private LsAdjustPointsResponseDTO createMockAdjustPointsResponse() {
        return LsAdjustPointsResponseDTO.builder()
                .status("SUCCESS")
                .message("Points adjusted successfully")
                .currentTier("GOLD")
                .membershipId(UUID.randomUUID())
                .transactionId(UUID.randomUUID())
                .build();
    }
}
