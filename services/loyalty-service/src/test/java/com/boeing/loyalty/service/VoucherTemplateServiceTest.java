package com.boeing.loyalty.service;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.boeing.loyalty.dto.voucher.CreateVoucherTemplateRequestDTO;
import com.boeing.loyalty.dto.voucher.VoucherTemplateResponseDTO;
import com.boeing.loyalty.entity.VoucherTemplate;
import com.boeing.loyalty.entity.enums.VoucherStatus;
import com.boeing.loyalty.repository.VoucherTemplateRepository;

@ExtendWith(MockitoExtension.class)
class VoucherTemplateServiceTest {

    @Mock
    private VoucherTemplateRepository voucherTemplateRepository;

    @InjectMocks
    private VoucherTemplateService voucherTemplateService;

    @Test
    void createVoucherTemplate_ShouldIncludePointsRequired() {
        // Given
        CreateVoucherTemplateRequestDTO request = CreateVoucherTemplateRequestDTO.builder()
                .code("TEST10")
                .name("Test Voucher")
                .description("Test voucher description")
                .discountPercentage(10)
                .minSpend(100.0)
                .maxDiscount(50.0)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(30))
                .usageLimit(100)
                .pointsRequired(200)
                .status("ACTIVE")
                .build();

        VoucherTemplate savedTemplate = VoucherTemplate.builder()
                .id(UUID.randomUUID())
                .code(request.getCode())
                .name(request.getName())
                .description(request.getDescription())
                .discountPercentage(request.getDiscountPercentage())
                .minSpend(request.getMinSpend())
                .maxDiscount(request.getMaxDiscount())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .usageLimit(request.getUsageLimit())
                .pointsRequired(request.getPointsRequired())
                .status(VoucherStatus.ACTIVE)
                .build();

        when(voucherTemplateRepository.save(any(VoucherTemplate.class))).thenReturn(savedTemplate);

        // When
        VoucherTemplateResponseDTO result = voucherTemplateService.createVoucherTemplate(request);

        // Then
        assertNotNull(result);
        assertEquals(200, result.getPointsRequired());
        assertEquals("TEST10", result.getCode());
        assertEquals("Test Voucher", result.getName());
    }
}
