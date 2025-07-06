package com.boeing.bookingservice.mapper;

import com.boeing.bookingservice.dto.request.PassengerInfoDTO;
import com.boeing.bookingservice.model.entity.Passenger;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

import java.util.List;
import java.util.UUID;

@Mapper(componentModel = "spring")
public interface PassengerMapper {

    PassengerMapper INSTANCE = Mappers.getMapper(PassengerMapper.class);

    @Mapping(target = "id", ignore = true)  // Ignore ID mapping, will be handled in service layer
    Passenger toPassengerEntity(PassengerInfoDTO dto);

    @Mapping(source = "id", target = "id", qualifiedByName = "uuidToInteger")
    PassengerInfoDTO passengerToPassengerInfoDTO(Passenger passenger);

    @Mapping(target = "id", ignore = true)  // Ignore ID mapping
    Passenger passengerInfoDTOToPassenger(PassengerInfoDTO passengerInfoDTO);

    List<PassengerInfoDTO> passengersToPassengerInfoDTOs(List<Passenger> passengers);

    @Mapping(target = "id", ignore = true)  // Ignore ID mapping during updates
    void updatePassengerFromDto(PassengerInfoDTO dto, @MappingTarget Passenger entity);
    
    @Named("uuidToInteger")
    default Integer uuidToInteger(UUID uuid) {
        return uuid != null ? uuid.hashCode() & 0x7fffffff : null;
    }
}

