package com.boeing.checkinservice.dto.requests;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;



@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddBoardingPassDto{
    @NotBlank(message = "not blank")
    private String barcode;
    private String seatCode;
    private String gate;
    private String sequenceNumber;
}
