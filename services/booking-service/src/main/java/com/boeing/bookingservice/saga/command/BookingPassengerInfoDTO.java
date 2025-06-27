package com.boeing.bookingservice.saga.command;

import com.boeing.bookingservice.dto.request.PassengerInfoDTO;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@AllArgsConstructor(access = AccessLevel.PUBLIC)
public class BookingPassengerInfoDTO {
    String seatCode;
    PassengerInfoDTO passengerDetails;
}