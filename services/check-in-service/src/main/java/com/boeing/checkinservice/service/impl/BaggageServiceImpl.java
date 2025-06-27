package com.boeing.checkinservice.service.impl;

import com.boeing.checkinservice.dto.requests.BaggageDto;
import com.boeing.checkinservice.dto.responses.BaggageDtoResponse;
import com.boeing.checkinservice.entity.Baggage;
import com.boeing.checkinservice.entity.BoardingPass;
import com.boeing.checkinservice.entity.enums.BaggageStatus;
import com.boeing.checkinservice.repository.BaggageRepository;
import com.boeing.checkinservice.repository.BoardingPassRepository;
import com.boeing.checkinservice.service.inte.BaggageService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BaggageServiceImpl implements BaggageService {

    private final BaggageRepository repository;

    private final BoardingPassRepository boardingPassRepository;

    private final ModelMapper modelMapper;
    private final BaggageRepository baggageRepository;

    @Override
    public Optional<?> getAllBaggage() {
        List<Baggage> baggages = repository.findAll();
        List<BaggageDtoResponse> dtos = baggages.stream().map(item -> modelMapper.map(item, BaggageDtoResponse.class)).toList();
        return Optional.of(dtos);
    }

    @Override
    public Optional<?> getBaggageById(UUID id) {
        Baggage baggage = repository.findById(id).orElseThrow(() -> new RuntimeException("Baggage not found"));
        BaggageDtoResponse response = modelMapper.map(baggage, BaggageDtoResponse.class);
        return Optional.of(response);
    }

    @Override
    public Optional<?> addBaggage(UUID boardingPassId, List<BaggageDto> baggageDto) {
        List<Baggage> listBaggage =  baggageDto.stream().map(item -> modelMapper.map(item, Baggage.class)).toList();

        BoardingPass boardingPass = boardingPassRepository.findById(boardingPassId).orElseThrow(() -> new RuntimeException("Boarding pass not found"));

        for (Baggage baggage : listBaggage) {
            baggage.setStatus(BaggageStatus.CREATED);
            baggage.setBoardingPass(boardingPass);
        }

        baggageRepository.saveAll(listBaggage);
        return Optional.of("Baggage added");
    }
}
