package com.boeing.checkinservice.dto.responses;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class CheckInReponse {
    private LocalDateTime checkInTime;

    private UUID boardingPassId;
}
