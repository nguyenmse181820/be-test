package com.boeing.flightservice.service.spec;

import com.boeing.flightservice.dto.request.*;
import com.boeing.flightservice.dto.response.*;
import com.boeing.flightservice.dto.union.Search;
import com.boeing.flightservice.entity.enums.FareType;
import org.springframework.http.converter.json.MappingJacksonValue;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface FlightService {
    MappingJacksonValue findAll(Map<String, String> params);

    FsSeatsAvailabilityResponseDTO checkSeatAvailability(UUID flightId, List<String> seatCodes);

    FsFlightWithFareDetailsDTO getFlightDetails(UUID flightId);

    FsConfirmSeatsResponseDTO confirmSeat(UUID flightId, FsConfirmSeatsRequestDTO request);

    FsReleaseSeatsResponseDTO releaseSeats(UUID flightId, FsReleaseSeatsRequestDTO request);

    FlightResponseDTO createFlight(FsFlightCreateRequest request);

    FlightResponseDTO updateFlight(UUID flightId, FsFlightCreateRequest request);

    FlightResponseDTO getFlightById(UUID flightId);

    Map<FareType, List<String>> getAircraftSeatSections(UUID aircraftId);

    @Deprecated
    FsReleaseFareResponseDTO releaseFare(UUID flightId, String fareName, FsReleaseFareRequestDTO request);

    @Deprecated
    FsConfirmFareSaleResponseDTO confirmFareSale(UUID flightId, String fareName, FsConfirmFareSaleRequestDTO request);

    @Deprecated
    int getAvailableSeatsCount(UUID flightId, String fareClass);

    Search.Response searchFlights(Search.Request request);
}