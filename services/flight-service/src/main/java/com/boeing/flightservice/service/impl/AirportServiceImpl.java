package com.boeing.flightservice.service.impl;

import com.boeing.flightservice.dto.paging.AirportDto;
import com.boeing.flightservice.repository.AirportRepository;
import com.boeing.flightservice.service.spec.AirportService;
import com.boeing.flightservice.util.PaginationUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.converter.json.MappingJacksonValue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

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
}