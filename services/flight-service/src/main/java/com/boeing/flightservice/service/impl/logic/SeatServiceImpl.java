package com.boeing.flightservice.service.impl.logic;

import com.boeing.flightservice.entity.Flight;
import com.boeing.flightservice.entity.FlightFare;
import com.boeing.flightservice.exception.BadRequestException;
import com.boeing.flightservice.service.cache.SeatPriceCacheService;
import com.boeing.flightservice.service.spec.logic.SeatService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class SeatServiceImpl implements SeatService {

    private final SeatPriceCacheService seatPriceCacheService;

    private List<String> expandSeatRange(String seatRange) {
        List<String> seatCodes = new ArrayList<>();

        if (seatRange == null || seatRange.isBlank()) return seatCodes;

        String[] parts = seatRange.split(",");
        for (String part : parts) {
            if (part.contains("-")) {
                seatCodes.addAll(expandRange(part));
            } else {
                seatCodes.add(part.trim());
            }
        }

        return seatCodes;
    }

    private List<String> expandRange(String range) {
        String[] bounds = range.split("-");
        if (bounds.length != 2) return Collections.emptyList();

        String start = bounds[0].trim();
        String end = bounds[1].trim();

        String startLetter = start.replaceAll("\\d", "");
        String endLetter = end.replaceAll("\\d", "");

        int startRow = Integer.parseInt(start.replaceAll("\\D", ""));
        int endRow = Integer.parseInt(end.replaceAll("\\D", ""));

        if (!startLetter.equals(endLetter)) return Collections.emptyList(); // e.g., A1-B3 not valid across rows

        List<String> result = new ArrayList<>();
        for (int i = startRow; i <= endRow; i++) {
            result.add(startLetter + i);
        }

        return result;
    }

    @Override
    public FlightFare findFareForSeat(String seatCode, List<FlightFare> fares) {
        for (FlightFare fare : fares) {
            List<String> expanded = expandSeatRange(fare.getSeatRange());
            if (expanded.contains(seatCode)) {
                return fare;
            }
        }
        throw new RuntimeException("Seat code " + seatCode + " is not assigned a fare!");
    }

    @Override
    public void validateSeatRanges(List<FlightFare> fares, List<String> seatMap) {
        Set<String> definedSeats = new HashSet<>();
        Set<String> allSeatMap = new HashSet<>(seatMap);
        List<String> duplicates = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        for (FlightFare fare : fares) {
            List<String> current = expandSeatRange(fare.getSeatRange());

            for (String seat : current) {
                if (!allSeatMap.contains(seat)) {
                    missing.add(seat);
                }
                if (!definedSeats.add(seat)) {
                    duplicates.add(seat);
                }
            }
        }

        if (!duplicates.isEmpty()) {
            throw new BadRequestException("Duplicate seats in multiple fares: " + duplicates);
        }

        Set<String> allDefinedSeats = new HashSet<>(definedSeats);
        allSeatMap.removeAll(allDefinedSeats);
        if (!allSeatMap.isEmpty()) {
            throw new BadRequestException("Missing seats not assigned to any fare: " + allSeatMap);
        }

        if (!missing.isEmpty()) {
            throw new BadRequestException("Invalid seats in seatRange not in seat map: " + missing);
        }
    }

    @Override
    public int countTotalSeats(List<FlightFare> fares) {
        Set<String> uniqueSeats = new HashSet<>();
        for (FlightFare fare : fares) {
            uniqueSeats.addAll(expandSeatRange(fare.getSeatRange()));
        }
        return uniqueSeats.size();
    }

    @Override
    public int countSeatsForFare(FlightFare fare) {
        List<String> seats = expandSeatRange(fare.getSeatRange());
        return seats.size();
    }

    @Override
    public int countRemainingSeats(FlightFare fare, List<String> occupiedSeats) {
        Set<String> seatSet = new HashSet<>(expandSeatRange(fare.getSeatRange()));
        for (String occupied : occupiedSeats) {
            seatSet.remove(occupied); // Only removes if it's actually in the range
        }
        return seatSet.size(); // Remaining seats
    }

    @Override
    public Double getSeatPrice(Flight flight, String seatCode) {
        FlightFare fare = findFareForSeat(seatCode, flight.getFares());
        Double price = seatPriceCacheService.get(flight.getId() + "_" + fare.getId());
        if (price == null) {
            price = ThreadLocalRandom.current().nextDouble(fare.getMinPrice(), fare.getMaxPrice());
            seatPriceCacheService.put(flight.getId() + "_" + fare.getId(), price);
        }
        return price;
    }
}
