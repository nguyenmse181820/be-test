package com.boeing.loyalty.dto.voucher;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserVoucherResponseDTO {
    private UUID id;
    private UUID membershipId;
    private VoucherTemplateResponseDTO voucherTemplate;
    private String code;
    private Double discountAmount;
    private Boolean isUsed;
    private LocalDateTime usedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
} 