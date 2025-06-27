package com.boeing.checkinservice.service.clients;

import com.boeing.checkinservice.dto.responses.FsFlightWithFareDetailsDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "flight-service", url = "${services.flight-service.url:http://localhost:8084}")
public interface FlightClient {

    @GetMapping("/flight-service/api/v1/fs/flights/{flightId}/details")
    FsFlightWithFareDetailsDTO getFlightDetails(@PathVariable("flightId") UUID flightId);

}
