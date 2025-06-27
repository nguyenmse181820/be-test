package com.boeing.flightservice.service.impl;

import com.boeing.flightservice.dto.paging.FlightFareDto;
import com.boeing.flightservice.repository.FlightFareRepository;
import com.boeing.flightservice.service.spec.FlightFareService;
import com.boeing.flightservice.util.PaginationUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.converter.json.MappingJacksonValue;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class FlightFareServiceImpl implements FlightFareService {

    private final FlightFareRepository flightFareRepository;

    @Override
    public MappingJacksonValue findAll(Map<String, String> params) {
        return PaginationUtil.findAll(
                params,
                flightFareRepository,
                FlightFareDto.class
        );
    }
}
