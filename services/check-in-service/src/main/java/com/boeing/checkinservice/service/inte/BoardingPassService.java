package com.boeing.checkinservice.service.inte;

import com.boeing.checkinservice.dto.requests.AddBoardingPassDto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BoardingPassService {

    Optional<?> getAllBoardingPassesByBookingReference(String bookingReference);

    Optional<?> getBoardingPassById(UUID id);

    Optional<?> addNewBoardingPass(List<AddBoardingPassDto> dto);

    Optional<?> checkInStatus(UUID booking_detail_id);
}
