package com.boeing.loyalty.dto.voucher;

import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserVoucherListResponseDTO {
    private List<UserVoucherResponseDTO> userVouchers;
    private int totalElements;
    private int totalPages;
    private int currentPage;
    private int pageSize;
} 