package com.boeing.bookingservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

/**
 * Result of flight analysis containing flight indices and metadata
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightAnalysisResult {
    
    /**
     * Map of flight ID to its index
     * Positive indices (1, 2, 3...) represent outbound flights
     * Negative indices (-1, -2, -3...) represent return flights
     */
    private Map<UUID, Integer> flightIndices;
    
    /**
     * The original departure airport code
     */
    private String originAirportCode;
    
    /**
     * The final destination airport code
     */
    private String finalDestinationAirportCode;
    
    /**
     * Whether this is a round-trip journey
     */
    private boolean isRoundTrip;
    
    /**
     * Whether this journey has connecting flights
     */
    private boolean hasConnectingFlights;
    
    /**
     * Number of outbound flight segments
     */
    private int outboundSegmentCount;
    
    /**
     * Number of return flight segments
     */
    private int returnSegmentCount;
    
    /**
     * Total number of flight segments
     */
    private int totalSegmentCount;
    
    /**
     * Index where the return journey starts (if applicable)
     */
    private Integer returnJourneyStartIndex;
}
