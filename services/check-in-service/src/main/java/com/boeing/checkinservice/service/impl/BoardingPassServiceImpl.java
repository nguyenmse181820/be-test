package com.boeing.checkinservice.service.impl;

import com.boeing.checkinservice.dto.requests.AddBoardingPassDto;
import com.boeing.checkinservice.dto.responses.BoardingPassDto;
import com.boeing.checkinservice.dto.responses.CheckInReponse;
import com.boeing.checkinservice.entity.Baggage;
import com.boeing.checkinservice.entity.BoardingPass;
import com.boeing.checkinservice.repository.BoardingPassRepository;
import com.boeing.checkinservice.service.clients.FlightClient;
import com.boeing.checkinservice.service.inte.BoardingPassService;
import jakarta.ws.rs.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.modelmapper.TypeMap;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BoardingPassServiceImpl implements BoardingPassService {

    private final BoardingPassRepository boardingPassRepository;

    private final ModelMapper mapper;

    private final FlightClient flightClient;

    @Override
    public Optional<?> getAllBoardingPasses() {
        List<BoardingPass> listEntity = boardingPassRepository.findAll();
        List<BoardingPassDto> listDto = listEntity.stream().map(
                item -> mapper.map(item, BoardingPassDto.class)
        ).toList();
        return Optional.of(listDto);
    }

    @Override
    public Optional<?> getBoardingPassById(UUID id) {
        BoardingPass entity = boardingPassRepository.findById(id).orElseThrow(() -> new BadRequestException("Not found any Boarding Pass"));

        BoardingPassDto dto = mapper.map(entity, BoardingPassDto.class);
        return Optional.of(dto);
    }

    @Override
    public Optional<?> addNewBoardingPass(AddBoardingPassDto dto, UUID flightId, UUID booking_detail_id) {
        TypeMap<AddBoardingPassDto, BoardingPass> typeMap =
                mapper.getTypeMap(AddBoardingPassDto.class, BoardingPass.class);

        if (typeMap == null) {
            typeMap = mapper.createTypeMap(AddBoardingPassDto.class, BoardingPass.class);
            typeMap.addMappings(mapper -> mapper.skip(BoardingPass::setBoardingTime));
        }

        BoardingPass entity = mapper.map(dto, BoardingPass.class);

        entity.setCheckinTime(LocalDateTime.now());
        entity.setIssuedAt(LocalDateTime.now());

        LocalDateTime departureTime = flightClient.getFlightDetails(flightId).departureTime();

        entity.setBoardingTime(departureTime.minusMinutes(30));

        entity.setBookingDetailId(booking_detail_id);

        BoardingPass response = boardingPassRepository.save(entity);

        CheckInReponse checkInReponse = new CheckInReponse();
        checkInReponse.setBoardingPassId(response.getId());
        checkInReponse.setCheckInTime(response.getCheckinTime());

        return Optional.of(checkInReponse);
    }

    @Override
    public Optional<?> checkInStatus(UUID booking_detail_id) {
        Boolean isChecked = boardingPassRepository.existsByBookingDetailId(booking_detail_id);
        return Optional.of(isChecked);
    }

}
