package com.boeing.bookingservice.model.enums;

/**
 * Enum representing different types of flight journeys
 */
public enum FlightType {
    
    /**
     * Single one-way flight
     */
    ONE_WAY,
    
    /**
     * Round-trip with direct flights (outbound + return)
     */
    ROUND_TRIP_DIRECT,
    
    /**
     * Round-trip with connecting flights on outbound, direct return
     */
    ROUND_TRIP_CONNECTING_OUTBOUND,
    
    /**
     * Round-trip with direct outbound, connecting return
     */
    ROUND_TRIP_CONNECTING_RETURN,
    
    /**
     * Round-trip with connecting flights on both outbound and return
     */
    ROUND_TRIP_CONNECTING_BOTH,
    
    /**
     * One-way journey with connecting flights
     */
    ONE_WAY_CONNECTING,
    
    /**
     * Multi-city journey (more complex itinerary)
     */
    MULTI_CITY,
    
    /**
     * Unknown or invalid flight type
     */
    UNKNOWN
}
