package com.boeing.loyalty.service;

import java.util.List;
import java.util.UUID;

import com.boeing.loyalty.dto.membership.EarnPointsRequestDTO;
import com.boeing.loyalty.dto.membership.EarnPointsResponseDTO;
import com.boeing.loyalty.dto.voucher.UseVoucherRequestDTO;
import com.boeing.loyalty.dto.voucher.UseVoucherResponseDTO;
import com.boeing.loyalty.dto.voucher.UserVoucherDTO;
import com.boeing.loyalty.dto.voucher.ValidateVoucherRequestDTO;
import com.boeing.loyalty.dto.voucher.ValidateVoucherResponseDTO;

public interface LoyaltyService {
    EarnPointsResponseDTO earnPoints(EarnPointsRequestDTO requestDTO);
    UseVoucherResponseDTO useVoucher(String voucherCode);
    List<UserVoucherDTO> getAllVoucher(UUID userId);
    ValidateVoucherResponseDTO validateVoucher(ValidateVoucherRequestDTO request);
    UseVoucherResponseDTO useVoucherWithRequest(UseVoucherRequestDTO request);
    String cancelVoucherUsage(String voucherCode, UUID userId);
    String adjustPoints(String bookingId);
    String cleanupOrphanedTransactions();
}
