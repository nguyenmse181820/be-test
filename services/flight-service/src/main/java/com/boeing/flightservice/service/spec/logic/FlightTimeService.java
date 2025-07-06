package com.boeing.flightservice.service.spec.logic;

public interface FlightTimeService {
    @Deprecated
    double calculateFlightTime(double lat1, double lon1, double lat2, double lon2);
}
