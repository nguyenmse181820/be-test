package com.boeing.bookingservice.service.impl;

import com.boeing.bookingservice.dto.request.PassengerInfoDTO;
import com.boeing.bookingservice.exception.ResourceNotFoundException;
import com.boeing.bookingservice.mapper.PassengerMapper;
import com.boeing.bookingservice.model.entity.Passenger;
import com.boeing.bookingservice.repository.PassengerRepository;
import com.boeing.bookingservice.service.PassengerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class PassengerServiceImpl implements PassengerService {

    private final PassengerRepository passengerRepository;
    private final PassengerMapper passengerMapper;

    @Autowired
    public PassengerServiceImpl(PassengerRepository passengerRepository, PassengerMapper passengerMapper) {
        this.passengerRepository = passengerRepository;
        this.passengerMapper = passengerMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PassengerInfoDTO> getPassengersByUser(UUID userId) {
        List<Passenger> passengers = passengerRepository.findByUserId(userId);
        return passengerMapper.passengersToPassengerInfoDTOs(passengers);
    }

    @Override
    @Transactional
    public PassengerInfoDTO createPassenger(PassengerInfoDTO passengerInfoDTO, UUID userId) {
        Passenger passenger = passengerMapper.passengerInfoDTOToPassenger(passengerInfoDTO);
        passenger.setUserId(userId);
        Passenger savedPassenger = passengerRepository.save(passenger);
        return passengerMapper.passengerToPassengerInfoDTO(savedPassenger);
    }

    @Override
    @Transactional
    public PassengerInfoDTO updatePassenger(UUID id, PassengerInfoDTO passengerInfoDTO) {
        Passenger existingPassenger = passengerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Passenger not found with id: " + id));

        passengerMapper.updatePassengerFromDto(passengerInfoDTO, existingPassenger);
        existingPassenger.setId(id);

        Passenger updatedPassenger = passengerRepository.save(existingPassenger);
        return passengerMapper.passengerToPassengerInfoDTO(updatedPassenger);
    }
}
