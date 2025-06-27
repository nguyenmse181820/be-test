package com.boeing.loyalty.dto.membership;

import com.boeing.loyalty.entity.enums.MembershipTier;
import jakarta.validation.constraints.Min;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMembershipRequestDTO {
    private MembershipTier tier;
    
    @Min(value = 0, message = "Points cannot be negative")
    private Integer points;
    
    @Min(value = 0, message = "Total earned points cannot be negative")
    private Integer totalEarnedPoints;
    
    @Min(value = 0, message = "Total spent cannot be negative")
    private Double totalSpent;
} 