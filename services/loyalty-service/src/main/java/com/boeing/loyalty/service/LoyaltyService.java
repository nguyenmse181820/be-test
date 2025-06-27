package com.boeing.loyalty.service;

import com.boeing.loyalty.dto.membership.EarnPointsRequestDTO;
import com.boeing.loyalty.dto.membership.EarnPointsResponseDTO;
import com.boeing.loyalty.dto.voucher.UseVoucherResponseDTO;
import com.boeing.loyalty.dto.voucher.UserVoucherDTO;

import java.util.List;
import java.util.UUID;

public interface LoyaltyService {
    EarnPointsResponseDTO earnPoints(EarnPointsRequestDTO requestDTO);
    UseVoucherResponseDTO useVoucher(String voucherCode);
    List<UserVoucherDTO> getAllVoucher(UUID userId);
    String adjustPoints(String bookingId);
}
