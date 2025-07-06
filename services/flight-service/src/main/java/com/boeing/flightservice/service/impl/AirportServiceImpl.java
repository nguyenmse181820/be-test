package com.boeing.flightservice.service.impl;

import com.boeing.flightservice.dto.paging.AirportDto;
import com.boeing.flightservice.dto.union.AirportDTO;
import com.boeing.flightservice.entity.Airport;
import com.boeing.flightservice.exception.BadRequestException;
import com.boeing.flightservice.repository.AirportRepository;
import com.boeing.flightservice.service.spec.AirportService;
import com.boeing.flightservice.util.PaginationUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.converter.json.MappingJacksonValue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class AirportServiceImpl implements AirportService {
    private final AirportRepository airportRepository;

    @Override
    public MappingJacksonValue findAll(Map<String, String> params) {
        return PaginationUtil.findAll(
                params,
                airportRepository,
                AirportDto.class
        );
    }

    @Override
    public AirportDTO.Response createAirport(AirportDTO.CreateRequest request) {
        Airport airport = Airport.builder()
                .name(request.name())
                .code(request.code())
                .city(request.city())
                .country(request.country())
                .timezone(request.timezone())
                .latitude(request.latitude())
                .longitude(request.longitude())
                .build();
        return AirportDTO.fromEntity(airportRepository.save(airport));
    }

    @Override
    public AirportDTO.Response updateAirport(UUID airportId, AirportDTO.UpdateRequest request) {
        Airport airport = airportRepository.findByIdAndDeleted(airportId, false)
                .orElseThrow(() -> new BadRequestException("Airport not found with id: " + airportId));
        return AirportDTO.fromEntity(airportRepository.save(airport));
    }

    @Override
    public void deleteAirport(UUID id) {
        Airport airport = airportRepository.findByIdAndDeleted(id, false)
                .orElseThrow(() -> new BadRequestException("Airport not found with id: " + id));
        airport.setDeleted(true);
        airportRepository.save(airport);
    }
}