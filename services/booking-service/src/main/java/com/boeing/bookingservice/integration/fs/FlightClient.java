package com.boeing.bookingservice.integration.fs;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.boeing.bookingservice.integration.fs.dto.FsConfirmFareSaleRequestDTO;
import com.boeing.bookingservice.integration.fs.dto.FsConfirmFareSaleResponseDTO;
import com.boeing.bookingservice.integration.fs.dto.FsConfirmSeatsRequestDTO;
import com.boeing.bookingservice.integration.fs.dto.FsConfirmSeatsResponseDTO;
import com.boeing.bookingservice.integration.fs.dto.FsFareAvailabilityResponseDTO;
import com.boeing.bookingservice.integration.fs.dto.FsFlightOptionDTO;
import com.boeing.bookingservice.integration.fs.dto.FsFlightWithFareDetailsDTO;
import com.boeing.bookingservice.integration.fs.dto.FsReleaseFareRequestDTO;
import com.boeing.bookingservice.integration.fs.dto.FsReleaseFareResponseDTO;
import com.boeing.bookingservice.integration.fs.dto.FsReleaseSeatsRequestDTO;
import com.boeing.bookingservice.integration.fs.dto.FsReleaseSeatsResponseDTO;
import com.boeing.bookingservice.integration.fs.dto.FsSeatsAvailabilityResponseDTO;
import com.boeing.bookingservice.dto.response.FlightDetailDTO;

@FeignClient(name = "flight-service", url = "${services.flight-service.url}")
public interface FlightClient {

    @GetMapping("/flight-service/api/v1/fs/flights/search")
    List<FsFlightOptionDTO> searchFlights(
            @RequestParam("originAirportCode") String originAirportCode,
            @RequestParam("destinationAirportCode") String destinationAirportCode,
            @RequestParam("departureDate") LocalDate departureDate,
            @RequestParam("passengerCount") int passengerCount
    );

    @GetMapping("/flight-service/api/v1/fs/flights/{flightId}/details")
    FsFlightWithFareDetailsDTO getFlightDetails(@PathVariable("flightId") UUID flightId);

    @GetMapping("/flight-service/api/v1/fs/flights/{flightId}/fares/{fareName}/check-availability")
    FsFareAvailabilityResponseDTO checkFareAvailability(
            @PathVariable("flightId") UUID flightId,
            @PathVariable("fareName") String fareName,
            @RequestParam("count") int requestedCount
    );

    @PostMapping("/flight-service/api/v1/fs/flights/{flightId}/fares/{fareIdentifier}/confirm-sale")
    FsConfirmFareSaleResponseDTO confirmFareSale(
            @PathVariable("flightId") UUID flightId,
            @PathVariable("fareIdentifier") String fareIdentifier,
            @RequestBody FsConfirmFareSaleRequestDTO request
    );

    @PostMapping("/flight-service/api/v1/fs/flights/{flightId}/fares/{fareName}/release")
    FsReleaseFareResponseDTO releaseFare(
            @PathVariable("flightId") UUID flightId,
            @PathVariable("fareName") String fareName,
            @RequestBody FsReleaseFareRequestDTO request
    );

    @PostMapping("/flight-service/api/v1/fs/flights/{flightId}/seats/confirm")
    FsConfirmSeatsResponseDTO confirmSeats(
            @PathVariable("flightId") UUID flightId,
            @RequestBody FsConfirmSeatsRequestDTO request
    );

    @GetMapping("/flight-service/api/v1/fs/flights/{flightId}/seats/check-availability")
    FsSeatsAvailabilityResponseDTO checkSeatsAvailability(
            @PathVariable("flightId") UUID flightId,
            @RequestParam("seatCodes") List<String> seatCodes
    );

    @PostMapping("/flight-service/api/v1/fs/flights/{flightId}/seats/release")
    FsReleaseSeatsResponseDTO releaseSeats(
            @PathVariable("flightId") UUID flightId,
            @RequestBody FsReleaseSeatsRequestDTO request
    );

    @GetMapping("/flight-service/api/v1/fs/flights/{flightId}/details")
    FsFlightWithFareDetailsDTO getFlightBasicDetails(@PathVariable("flightId") UUID flightId);

}