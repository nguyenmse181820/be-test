package com.boeing.flightservice.service.spec;

import com.boeing.flightservice.dto.union.BenefitDTO;
import org.springframework.http.converter.json.MappingJacksonValue;

import java.util.Map;
import java.util.UUID;

public interface BenefitService {
    MappingJacksonValue findAll(Map<String, String> params);

    BenefitDTO.Response createBenefit(BenefitDTO.CreateRequest request);

    BenefitDTO.Response updateBenefit(UUID id, BenefitDTO.UpdateRequest request);

    void deleteBenefit(UUID id);
}
