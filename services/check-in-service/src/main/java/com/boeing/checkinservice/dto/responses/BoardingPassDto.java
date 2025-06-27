package com.boeing.checkinservice.dto.responses;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BoardingPassDto {
    private UUID id;
    private String barcode;
    private LocalDateTime boardingTime;
    private LocalDateTime issuedAt;
    private LocalDateTime checkinTime;
    private String seatCode;
    private String gate;
    private String sequenceNumber;
}


