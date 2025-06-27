package com.boeing.flightservice.controller;

import com.boeing.flightservice.annotation.StandardAPIResponses;
import com.boeing.flightservice.annotation.StandardGetParams;
import com.boeing.flightservice.service.spec.BenefitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.converter.json.MappingJacksonValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Tag(name = "Benefits", description = "Flight benefit management API")
@RequiredArgsConstructor
public class BenefitController {

    private final BenefitService benefitService;

    @GetMapping("/api/v1/benefits")
    @Operation(summary = "Get all benefits", description = "Get all benefits with optional filtering, sorting, and pagination")
    @StandardGetParams
    @StandardAPIResponses
    public MappingJacksonValue getAllBenefits(@RequestParam Map<String, String> params) {
        return benefitService.findAll(params);
    }
}
