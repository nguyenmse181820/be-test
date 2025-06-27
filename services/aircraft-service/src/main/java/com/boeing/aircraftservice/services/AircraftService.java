package com.boeing.aircraftservice.services;

import com.boeing.aircraftservice.dtos.request.CreateAircraftRequest;
import com.boeing.aircraftservice.dtos.request.SearchAircraftCodeRequest;
import com.boeing.aircraftservice.dtos.request.UpdateAircraftRequest;
import com.boeing.aircraftservice.dtos.response.AircraftResponseDTO;
import com.boeing.aircraftservice.dtos.response.PagingResponse;

import java.util.List;
import java.util.UUID;

public interface AircraftService {

    PagingResponse getAircraftsPaging(Integer currentPage, Integer pageSize);

    PagingResponse getAircraftsActive(Integer currentPage, Integer pageSize);

    List<AircraftResponseDTO> getAircrafts();

    AircraftResponseDTO findById(UUID AircraftID);

    AircraftResponseDTO findByIdActive(UUID AircraftID);

    AircraftResponseDTO findByCode(SearchAircraftCodeRequest code);

    AircraftResponseDTO unDeleteAircraft(UUID AircraftID);

    AircraftResponseDTO deleteAircraft(UUID AircraftID);

    AircraftResponseDTO createAircraft(CreateAircraftRequest createAircraftRequest);

    AircraftResponseDTO updateAircraft(UpdateAircraftRequest updateAircraftRequest, UUID aircraftID);

    PagingResponse searchAircrafts(Integer currentPage, Integer pageSize, String code, String model, String manufacturer);

    PagingResponse searchAircraftsActive(Integer currentPage, Integer pageSize, String code, String model, String manufacturer);
}
