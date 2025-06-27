package com.boeing.bookingservice.integration.fs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FsFareRuleDTO {
    private String cancellationPolicySummary;
    private String reschedulePolicySummary;
    private Double cancellationFeeFixed;
    private Double cancellationFeePercentage;
    private Integer cancellationAllowedBeforeHours;
    private Double rescheduleFeeFixed;
    private Double rescheduleFeePercentage;
    private Integer rescheduleAllowedBeforeHours;
}
