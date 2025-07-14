package com.boeing.aircraftservice.services;

import com.boeing.aircraftservice.dtos.request.*;
import com.boeing.aircraftservice.dtos.response.AircraftResponseDTO;
import com.boeing.aircraftservice.dtos.response.AircraftTypeResponseDTO;
import com.boeing.aircraftservice.dtos.response.PagingResponse;

import java.util.List;
import java.util.UUID;

public interface AircraftTypeService {
    PagingResponse getAircraftsTypePaging(Integer currentPage, Integer pageSize);

    PagingResponse getAircraftsTypeActive(Integer currentPage, Integer pageSize);

    List<AircraftTypeResponseDTO> getAircraftsType();

    List<AircraftTypeResponseDTO> getAircraftsTypeActive();

    AircraftTypeResponseDTO findById(UUID aircraftTypeID);

//    AircraftTypeResponseDTO findByModel(SearchAircraftCodeRequest model);
//
//    AircraftTypeResponseDTO findByManufacturer(SearchAircraftCodeRequest manufacturer);

    AircraftTypeResponseDTO unDeleteAircraftType(UUID AircraftID);

    AircraftTypeResponseDTO deleteAircraftType(UUID AircraftID);

    AircraftTypeResponseDTO createAircraftType(CreateAircraftTypeV2Request createAircraftTypeRequest);

    AircraftTypeResponseDTO updateAircraftType(UpdateAircraftTypeV2Request updateAircraftTypeRequest, UUID aircraftID);

    PagingResponse searchAircraftsType(Integer currentPage, Integer pageSize, String model, String manufacturer);

    List<AircraftResponseDTO> getAllAircraftByAircraftTypeID(UUID AircraftTypeID);

}
