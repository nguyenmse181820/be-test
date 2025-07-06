package com.boeing.flightservice.service.impl;

import com.boeing.flightservice.dto.paging.BenefitDto;
import com.boeing.flightservice.dto.union.BenefitDTO;
import com.boeing.flightservice.entity.Benefit;
import com.boeing.flightservice.exception.BadRequestException;
import com.boeing.flightservice.repository.BenefitRepository;
import com.boeing.flightservice.service.spec.BenefitService;
import com.boeing.flightservice.util.PaginationUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.converter.json.MappingJacksonValue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

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

    @Override
    @Transactional
    public BenefitDTO.Response createBenefit(BenefitDTO.CreateRequest request) {
        Benefit benefit = Benefit.builder()
                .name(request.name())
                .description(request.description())
                .iconURL(request.iconURL())
                .build();
        return BenefitDTO.fromEntity(benefitRepository.save(benefit));
    }

    @Override
    @Transactional
    public BenefitDTO.Response updateBenefit(UUID id, BenefitDTO.UpdateRequest request) {
        Benefit benefit = benefitRepository.findByIdAndDeleted(id, false)
                .orElseThrow(() -> new BadRequestException("Benefit not found with id: " + id));
        if (request.name() != null) {
            benefit.setName(request.name());
        }
        if (request.description() != null) {
            benefit.setDescription(request.description());
        }
        if (request.iconURL() != null) {
            benefit.setIconURL(request.iconURL());
        }
        return BenefitDTO.fromEntity(benefitRepository.save(benefit));
    }

    @Override
    @Transactional
    public void deleteBenefit(UUID id) {
        Benefit benefit = benefitRepository.findByIdAndDeleted(id, false)
                .orElseThrow(() -> new BadRequestException("Benefit not found with id: " + id));
        benefit.setDeleted(true);
        benefitRepository.save(benefit);
    }
}
