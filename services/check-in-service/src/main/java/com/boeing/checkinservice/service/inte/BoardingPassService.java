package com.boeing.checkinservice.service.inte;

import com.boeing.checkinservice.dto.requests.AddBoardingPassDto;
import com.boeing.checkinservice.dto.responses.BookingDetailInfoDTO;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BoardingPassService {

    Optional<?> getAllBoardingPassesByBookingReference(List<BookingDetailInfoDTO> details);

    Optional<?> getBoardingPassById(UUID id);

    Optional<?> addNewBoardingPass(List<AddBoardingPassDto> dto);

    Optional<?> checkInStatus(List<UUID> booking_detail_ids);
}
