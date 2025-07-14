package com.boeing.bookingservice.integration.ls.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LsValidateVoucherRequestDTO {
    private String voucherCode;
    private UUID userId;
    private Double bookingAmount;
    private String flightCode;
}