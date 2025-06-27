package com.boeing.checkinservice.service.inte;

import com.boeing.checkinservice.dto.requests.AddBoardingPassDto;

import java.util.Optional;
import java.util.UUID;

public interface BoardingPassService {

    Optional<?> getAllBoardingPasses();

    Optional<?> getBoardingPassById(UUID id);

    Optional<?> addNewBoardingPass(AddBoardingPassDto dto, UUID flightId, UUID booking_detail_id);

    Optional<?> checkInStatus(UUID booking_detail_id);
}
