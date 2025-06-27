package com.boeing.flightservice.service.impl;

import com.boeing.flightservice.dto.paging.BenefitDto;
import com.boeing.flightservice.repository.BenefitRepository;
import com.boeing.flightservice.service.spec.BenefitService;
import com.boeing.flightservice.util.PaginationUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.converter.json.MappingJacksonValue;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class BenefitServiceImpl implements BenefitService {

    private final BenefitRepository benefitRepository;

    @Override
    public MappingJacksonValue findAll(Map<String, String> params) {
        return PaginationUtil.findAll(
                params,
                benefitRepository,
                BenefitDto.class
        );
    }
}
