package com.boeing.aircraftservice.mappers;

import com.boeing.aircraftservice.dtos.response.AircraftTypeResponseDTO;
import com.boeing.aircraftservice.pojos.AircraftType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AircraftTypeMapper {

    @Mapping(source = "id", target = "id")
    @Mapping(source = "seatMap", target = "seatMap")
    @Mapping(source = "totalSeats", target = "totalSeats")
    @Mapping(source = "deleted", target = "deleted")
    AircraftTypeResponseDTO aircraftTypetoAircraftTypeResponseDTO(AircraftType aircraftType);

}
