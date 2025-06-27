package com.boeing.loyalty.dto.voucher;

import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoucherTemplateListResponseDTO {
    private List<VoucherTemplateResponseDTO> voucherTemplates;
    private int totalElements;
    private int totalPages;
    private int currentPage;
    private int pageSize;
} 