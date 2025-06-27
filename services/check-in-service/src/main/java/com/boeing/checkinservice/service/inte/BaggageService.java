package com.boeing.checkinservice.service.inte;

import com.boeing.checkinservice.dto.requests.BaggageDto;
import com.boeing.checkinservice.entity.Baggage;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BaggageService {

    Optional<?> getAllBaggage();

    Optional<?> getBaggageById(UUID id);

    Optional<?> addBaggage(UUID boardingPassId, List<BaggageDto> baggageDto);
}
