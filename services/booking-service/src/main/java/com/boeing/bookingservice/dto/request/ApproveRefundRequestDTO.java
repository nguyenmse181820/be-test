package com.boeing.bookingservice.dto.request;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApproveRefundRequestDTO {
    private String notes;
    private String transactionProofUrl;
}
