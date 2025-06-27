package com.boeing.flightservice.service.spec;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.converter.json.MappingJacksonValue;

import com.boeing.flightservice.dto.request.FsConfirmSeatsRequestDTO;
import com.boeing.flightservice.dto.request.FsFlightCreateRequestV2;
import com.boeing.flightservice.dto.request.FsReleaseSeatsRequestDTO;
import com.boeing.flightservice.dto.response.FlightResponseDTO;
import com.boeing.flightservice.dto.response.FsConfirmSeatsResponseDTO;
import com.boeing.flightservice.dto.response.FsFlightWithFareDetailsDTO;
import com.boeing.flightservice.dto.response.FsReleaseSeatsResponseDTO;
import com.boeing.flightservice.dto.response.FsSeatsAvailabilityResponseDTO;

public interface FlightService {
    MappingJacksonValue findAll(Map<String, String> params);

    FsSeatsAvailabilityResponseDTO checkSeatAvailability(UUID flightId, List<String> seatCodes);
    FsFlightWithFareDetailsDTO getFlightDetails(UUID flightId);
    FsConfirmSeatsResponseDTO confirmSeat(UUID flightId, FsConfirmSeatsRequestDTO request);
    FsReleaseSeatsResponseDTO releaseSeats(UUID flightId, FsReleaseSeatsRequestDTO request);
//    FlightResponseDTO createFlight(FsFlightCreateRequest fsFlightCreateRequest);
    FlightResponseDTO createFlightV2(FsFlightCreateRequestV2 request);
    Map<String, List<String>> getAircraftSeatSections(UUID aircraftId);
    int getAvailableSeatsCount(UUID flightId, String fareClass);
}