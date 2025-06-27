package com.boeing.flightservice.service.spec.logic;

import com.boeing.flightservice.entity.Flight;
import com.boeing.flightservice.entity.FlightFare;

import java.util.List;

public interface SeatService {
    FlightFare findFareForSeat(String seatCode, List<FlightFare> fares);

    void validateSeatRanges(List<FlightFare> fares, List<String> seatMap);

    int countTotalSeats(List<FlightFare> fares);

    int countSeatsForFare(FlightFare fare);

    int countRemainingSeats(FlightFare fare, List<String> occupiedSeats);

    Double getSeatPrice(Flight flight, String seatCode);
}
