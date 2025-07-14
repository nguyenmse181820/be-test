package com.boeing.checkinservice.dto.responses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoardingPassDto {
    private UUID id;
    private String barcode;
    private LocalDateTime boardingTime;
    private LocalDateTime issuedAt;
    private LocalDateTime checkinTime;
    private String seatCode;
    private String from;
    private String to;
    private LocalDateTime arrivalTime;
    private String classType;
    private String passengerName;
    private String title;
    private String flightCode;
    private List<BaggageDtoResponse> baggages;
//    private String gate;
//    private String sequenceNumber;
}


