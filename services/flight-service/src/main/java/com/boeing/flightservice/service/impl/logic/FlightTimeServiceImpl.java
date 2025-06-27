package com.boeing.flightservice.service.impl.logic;

import com.boeing.flightservice.service.spec.logic.FlightTimeService;
import org.springframework.stereotype.Service;

@Service
public class FlightTimeServiceImpl implements FlightTimeService {

    @Override
    public double calculateFlightTime(double lat1, double lon1, double lat2, double lon2) {
        double earthRadius = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = earthRadius * c;

        return (distance / 900.0) * 60; // Flight time in minutes (assuming 900km/h)
    }

}
