package com.boeing.bookingservice.integration.fs.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.UUID;

@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FsSeatsAvailabilityResponseDTO {

    List<SeatStatus> seatStatuses;
    boolean allRequestedSeatsAvailable;

    @Builder
    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class SeatStatus {
        String seatCode;
        boolean available;
        FareDetail fare;
    }

    @Builder
    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class FareDetail {
        UUID id;
        String name;
        Double price;
    }
}
