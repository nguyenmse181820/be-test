package com.boeing.flightservice.service.spec.logic;

import com.boeing.flightservice.entity.Flight;
import com.boeing.flightservice.entity.FlightFare;
import lombok.Builder;

import java.util.List;
import java.util.Map;

public interface SeatService {

    FarePrice getSeatFareAndPrice(Flight flight, String seatCode);

    @Builder
    record FarePrice(FlightFare fare, Double price) {
    }

    //--------------------------------------------------------------------------------------------------------
    @Deprecated
    FlightFare findFareForSeat(String seatCode, List<FlightFare> fares);

    @Deprecated
    void validateSeatRanges(List<FlightFare> fares, List<String> seatMap);

    @Deprecated
    int countTotalSeats(List<FlightFare> fares);

    @Deprecated
    int countSeatsForFare(FlightFare fare);

    @Deprecated
    int countRemainingSeats(FlightFare fare, List<String> occupiedSeats);

}
