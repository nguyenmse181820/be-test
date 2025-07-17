package com.boeing.bookingservice.service.impl;

import com.boeing.bookingservice.dto.response.FlightAnalysisResult;
import com.boeing.bookingservice.dto.response.FlightDetailDTO;
import com.boeing.bookingservice.integration.fs.FlightClient;
import com.boeing.bookingservice.model.enums.FlightType;
import com.boeing.bookingservice.service.FlightAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Smart implementation of FlightAnalysisService that efficiently analyzes 
 * flight relationships and determines flight types and ordering indices.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FlightAnalysisServiceImpl implements FlightAnalysisService {
    
    private final FlightClient flightClient;
    
    @Override
    public FlightAnalysisResult analyzeFlightIndices(List<UUID> flightIds) {
        if (flightIds == null || flightIds.isEmpty()) {
            return createEmptyResult();
        }
        
        try {
            // Get flight details in batch for efficiency
            List<FlightDetailDTO> flights = getFlightDetailsBatch(flightIds);
            
            if (flights.isEmpty()) {
                log.warn("No flight details found for provided flight IDs");
                return createEmptyResult();
            }
            
            // Sort flights by departure time to ensure chronological order
            flights.sort(Comparator.comparing(FlightDetailDTO::getDepartureTime));
            
            // Analyze flight connections and assign indices
            return performFlightAnalysis(flights);
            
        } catch (Exception e) {
            log.error("Error analyzing flight indices for flight IDs: {}", flightIds, e);
            return createEmptyResult();
        }
    }
    
    @Override
    public FlightType determineFlightType(FlightAnalysisResult analysisResult) {
        if (analysisResult == null || analysisResult.getFlightIndices().isEmpty()) {
            return FlightType.UNKNOWN;
        }
        
        boolean isRoundTrip = analysisResult.isRoundTrip();
        boolean hasConnecting = analysisResult.isHasConnectingFlights();
        int outboundCount = analysisResult.getOutboundSegmentCount();
        int returnCount = analysisResult.getReturnSegmentCount();
        
        if (!isRoundTrip) {
            return hasConnecting ? FlightType.ONE_WAY_CONNECTING : FlightType.ONE_WAY;
        }
        
        // Round-trip scenarios
        if (outboundCount == 1 && returnCount == 1) {
            return FlightType.ROUND_TRIP_DIRECT;
        } else if (outboundCount > 1 && returnCount == 1) {
            return FlightType.ROUND_TRIP_CONNECTING_OUTBOUND;
        } else if (outboundCount == 1 && returnCount > 1) {
            return FlightType.ROUND_TRIP_CONNECTING_RETURN;
        } else if (outboundCount > 1 && returnCount > 1) {
            return FlightType.ROUND_TRIP_CONNECTING_BOTH;
        }
        
        return FlightType.MULTI_CITY;
    }
    
    @Override
    public boolean validateFlightSequence(List<UUID> flightIds) {
        try {
            List<FlightDetailDTO> flights = getFlightDetailsBatch(flightIds);
            
            if (flights.size() != flightIds.size()) {
                log.warn("Some flights not found during validation");
                return false;
            }
            
            // Sort by departure time
            flights.sort(Comparator.comparing(FlightDetailDTO::getDepartureTime));
            
            // Validate chronological order and airport connections
            return validateFlightConnections(flights);
            
        } catch (Exception e) {
            log.error("Error validating flight sequence", e);
            return false;
        }
    }
    
    private List<FlightDetailDTO> getFlightDetailsBatch(List<UUID> flightIds) {
        try {
            // Use batch API if available, otherwise fallback to individual calls
            return flightClient.getFlightDetailsBatch(flightIds);
        } catch (Exception e) {
            log.warn("Batch flight details not available, falling back to individual calls");
            return getFlightDetailsIndividually(flightIds);
        }
    }
    
    private List<FlightDetailDTO> getFlightDetailsIndividually(List<UUID> flightIds) {
        List<FlightDetailDTO> flights = new ArrayList<>();
        
        for (UUID flightId : flightIds) {
            try {
                FlightDetailDTO flight = flightClient.getFlightBasicDetails(flightId);
                if (flight != null) {
                    flights.add(flight);
                }
            } catch (Exception e) {
                log.error("Error fetching flight details for ID: {}", flightId, e);
            }
        }
        
        return flights;
    }
    
    private FlightAnalysisResult performFlightAnalysis(List<FlightDetailDTO> flights) {
        Map<UUID, Integer> flightIndices = new HashMap<>();
        
        if (flights.isEmpty()) {
            return createEmptyResult();
        }
        
        String originAirport = flights.get(0).getDepartureAirportCode();
        String finalDestination = flights.get(flights.size() - 1).getArrivalAirportCode();
        
        // Detect return journey start point using smart algorithm
        int returnJourneyStartIndex = detectReturnJourneyStart(flights, originAirport);
        
        // Assign indices based on journey direction
        assignFlightIndices(flights, flightIndices, returnJourneyStartIndex);
        
        // Calculate metadata
        int outboundCount = (int) flightIndices.values().stream()
                .filter(index -> index > 0)
                .count();
        int returnCount = (int) flightIndices.values().stream()
                .filter(index -> index < 0)
                .count();
        
        boolean isRoundTrip = returnCount > 0;
        boolean hasConnecting = outboundCount > 1 || returnCount > 1;
        
        return FlightAnalysisResult.builder()
                .flightIndices(flightIndices)
                .originAirportCode(originAirport)
                .finalDestinationAirportCode(finalDestination)
                .isRoundTrip(isRoundTrip)
                .hasConnectingFlights(hasConnecting)
                .outboundSegmentCount(outboundCount)
                .returnSegmentCount(returnCount)
                .totalSegmentCount(flights.size())
                .returnJourneyStartIndex(returnJourneyStartIndex >= 0 ? returnJourneyStartIndex : null)
                .build();
    }
    
    private int detectReturnJourneyStart(List<FlightDetailDTO> flights, String originAirport) {
        // Smart algorithm to detect where return journey starts
        // Look for flights that eventually lead back to origin
        
        for (int i = 1; i < flights.size(); i++) {
            FlightDetailDTO currentFlight = flights.get(i);
            
            // Check if this flight or any subsequent flight returns to origin
            if (currentFlight.getArrivalAirportCode().equals(originAirport)) {
                return i;
            }
            
            // Check if this is part of a return journey by looking ahead
            if (i < flights.size() - 1) {
                // Look for pattern where we're heading back towards origin
                boolean isHeadingBackToOrigin = isFlightSequenceReturning(flights, i, originAirport);
                if (isHeadingBackToOrigin) {
                    return i;
                }
            }
        }
        
        // Check if the last flight ends at origin (simple round trip)
        FlightDetailDTO lastFlight = flights.get(flights.size() - 1);
        if (lastFlight.getArrivalAirportCode().equals(originAirport)) {
            // Find where the return journey logically starts
            return findReturnJourneyStartByDistance(flights, originAirport);
        }
        
        return -1; // No return journey detected
    }
    
    private boolean isFlightSequenceReturning(List<FlightDetailDTO> flights, int startIndex, String originAirport) {
        // Check if the flight sequence from startIndex eventually returns to origin
        for (int i = startIndex; i < flights.size(); i++) {
            if (flights.get(i).getArrivalAirportCode().equals(originAirport)) {
                return true;
            }
        }
        return false;
    }
    
    private int findReturnJourneyStartByDistance(List<FlightDetailDTO> flights, String originAirport) {
        // Find the furthest point from origin and assume return journey starts from there
        String furthestDestination = null;
        int furthestIndex = -1;
        
        for (int i = 0; i < flights.size(); i++) {
            String destination = flights.get(i).getArrivalAirportCode();
            // In a real implementation, you might calculate actual distances
            // For now, we'll use a simple heuristic
            if (furthestDestination == null || !destination.equals(originAirport)) {
                furthestDestination = destination;
                furthestIndex = i;
            }
        }
        
        // Return journey starts from the flight after reaching the furthest destination
        return furthestIndex + 1 < flights.size() ? furthestIndex + 1 : -1;
    }
    
    private void assignFlightIndices(List<FlightDetailDTO> flights, Map<UUID, Integer> flightIndices, int returnJourneyStartIndex) {
        int outboundIndex = 1;
        int returnIndex = -1;
        
        for (int i = 0; i < flights.size(); i++) {
            FlightDetailDTO flight = flights.get(i);
            
            if (returnJourneyStartIndex >= 0 && i >= returnJourneyStartIndex) {
                flightIndices.put(flight.getId(), returnIndex--);
            } else {
                flightIndices.put(flight.getId(), outboundIndex++);
            }
        }
    }
    
    private boolean validateFlightConnections(List<FlightDetailDTO> flights) {
        for (int i = 0; i < flights.size() - 1; i++) {
            FlightDetailDTO currentFlight = flights.get(i);
            FlightDetailDTO nextFlight = flights.get(i + 1);
            
            // Check if arrival airport matches departure airport of next flight
            if (!currentFlight.getArrivalAirportCode().equals(nextFlight.getDepartureAirportCode())) {
                log.warn("Flight connection mismatch: {} -> {} and {} -> {}",
                        currentFlight.getDepartureAirportCode(),
                        currentFlight.getArrivalAirportCode(),
                        nextFlight.getDepartureAirportCode(),
                        nextFlight.getArrivalAirportCode());
                return false;
            }
            
            // Check if there's enough time between flights (minimum 1 hour for connections)
            if (currentFlight.getArrivalTime().plusHours(1).isAfter(nextFlight.getDepartureTime())) {
                log.warn("Insufficient connection time between flights {} and {}", 
                        currentFlight.getFlightNumber(), nextFlight.getFlightNumber());
                return false;
            }
        }
        
        return true;
    }
    
    private FlightAnalysisResult createEmptyResult() {
        return FlightAnalysisResult.builder()
                .flightIndices(new HashMap<>())
                .isRoundTrip(false)
                .hasConnectingFlights(false)
                .outboundSegmentCount(0)
                .returnSegmentCount(0)
                .totalSegmentCount(0)
                .build();
    }
}
