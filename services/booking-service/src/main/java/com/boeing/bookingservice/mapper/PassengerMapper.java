package com.boeing.bookingservice.mapper;

import com.boeing.bookingservice.dto.request.PassengerInfoDTO;
import com.boeing.bookingservice.model.entity.Passenger;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PassengerMapper {

    PassengerMapper INSTANCE = Mappers.getMapper(PassengerMapper.class);

    Passenger toPassengerEntity(PassengerInfoDTO dto);

    PassengerInfoDTO passengerToPassengerInfoDTO(Passenger passenger);

    Passenger passengerInfoDTOToPassenger(PassengerInfoDTO passengerInfoDTO);

    List<PassengerInfoDTO> passengersToPassengerInfoDTOs(List<Passenger> passengers);

    void updatePassengerFromDto(PassengerInfoDTO dto, @MappingTarget Passenger entity);

}

