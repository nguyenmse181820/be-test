package com.boeing.bookingservice.service;

import com.boeing.bookingservice.dto.request.PassengerInfoDTO;
import java.util.List;
import java.util.UUID;

public interface PassengerService {
    List<PassengerInfoDTO> getPassengersByUser(UUID userId);
    PassengerInfoDTO createPassenger(PassengerInfoDTO passengerInfoDTO, UUID userId);
    PassengerInfoDTO updatePassenger(UUID id, PassengerInfoDTO passengerInfoDTO);
}

