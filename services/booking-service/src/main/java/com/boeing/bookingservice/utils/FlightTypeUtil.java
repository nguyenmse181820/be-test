package com.boeing.bookingservice.utils;

import com.boeing.bookingservice.dto.response.FlightAnalysisResult;
import com.boeing.bookingservice.model.entity.BookingDetail;
import com.boeing.bookingservice.model.enums.FlightType;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Utility class for flight type operations and flight index handling
 */
@UtilityClass
public class FlightTypeUtil {
    
    /**
     * Determines the flight direction based on flight index
     * 
     * @param flightIndex The flight index
     * @return "OUTBOUND" for positive indices, "RETURN" for negative indices, "UNKNOWN" for null/0
     */
    public static String determineFlightDirection(Integer flightIndex) {
        if (flightIndex == null || flightIndex == 0) {
            return "UNKNOWN";
        }
        
        return flightIndex > 0 ? "OUTBOUND" : "RETURN";
    }
    
    /**
     * Checks if a list of booking details represents a connecting flight
     * 
     * @param bookingDetails List of booking details
     * @return true if there are multiple segments in the same direction
     */
    public static boolean isConnectingFlight(List<BookingDetail> bookingDetails) {
        if (bookingDetails == null || bookingDetails.size() <= 1) {
            return false;
        }
        
        long outboundCount = bookingDetails.stream()
                .filter(bd -> bd.getFlightIndex() != null && bd.getFlightIndex() > 0)
                .count();
        
        long returnCount = bookingDetails.stream()
                .filter(bd -> bd.getFlightIndex() != null && bd.getFlightIndex() < 0)
                .count();
        
        return outboundCount > 1 || returnCount > 1;
    }
    
    /**
     * Checks if a list of booking details represents a round trip
     * 
     * @param bookingDetails List of booking details
     * @return true if there are both outbound and return flights
     */
    public static boolean isRoundTrip(List<BookingDetail> bookingDetails) {
        if (bookingDetails == null || bookingDetails.isEmpty()) {
            return false;
        }
        
        boolean hasOutbound = bookingDetails.stream()
                .anyMatch(bd -> bd.getFlightIndex() != null && bd.getFlightIndex() > 0);
        
        boolean hasReturn = bookingDetails.stream()
                .anyMatch(bd -> bd.getFlightIndex() != null && bd.getFlightIndex() < 0);
        
        return hasOutbound && hasReturn;
    }
    
    /**
     * Groups booking details by flight direction
     * 
     * @param bookingDetails List of booking details
     * @return Map with "OUTBOUND" and "RETURN" keys containing respective flights
     */
    public static Map<String, List<BookingDetail>> groupByDirection(List<BookingDetail> bookingDetails) {
        return bookingDetails.stream()
                .collect(Collectors.groupingBy(
                        bd -> determineFlightDirection(bd.getFlightIndex())
                ));
    }
    
    /**
     * Sorts booking details by flight index (outbound first, then return)
     * 
     * @param bookingDetails List of booking details to sort
     * @return Sorted list
     */
    public static List<BookingDetail> sortByFlightIndex(List<BookingDetail> bookingDetails) {
        return bookingDetails.stream()
                .sorted((a, b) -> {
                    Integer indexA = a.getFlightIndex();
                    Integer indexB = b.getFlightIndex();
                    
                    if (indexA == null && indexB == null) return 0;
                    if (indexA == null) return 1;
                    if (indexB == null) return -1;
                    
                    // Outbound flights (positive) come first, then return flights (negative)
                    if (indexA > 0 && indexB > 0) return Integer.compare(indexA, indexB);
                    if (indexA < 0 && indexB < 0) return Integer.compare(Math.abs(indexB), Math.abs(indexA));
                    if (indexA > 0 && indexB < 0) return -1;
                    if (indexA < 0 && indexB > 0) return 1;
                    
                    return 0;
                })
                .collect(Collectors.toList());
    }
    
    /**
     * Gets the journey summary from booking details
     * 
     * @param bookingDetails List of booking details
     * @return Journey summary string
     */
    public static String getJourneySummary(List<BookingDetail> bookingDetails) {
        if (bookingDetails == null || bookingDetails.isEmpty()) {
            return "No flights";
        }
        
        List<BookingDetail> sortedDetails = sortByFlightIndex(bookingDetails);
        
        StringBuilder summary = new StringBuilder();
        String previousDirection = "";
        
        for (BookingDetail detail : sortedDetails) {
            String direction = determineFlightDirection(detail.getFlightIndex());
            
            if (!direction.equals(previousDirection)) {
                if (summary.length() > 0) {
                    summary.append(" | ");
                }
                summary.append(direction).append(": ");
                previousDirection = direction;
            } else {
                summary.append(" -> ");
            }
            
            summary.append(detail.getOriginAirportCode())
                   .append("-")
                   .append(detail.getDestinationAirportCode());
        }
        
        return summary.toString();
    }
    
    /**
     * Validates that flight indices are consistent and logical
     * 
     * @param flightIndices Map of flight ID to index
     * @return true if indices are valid
     */
    public static boolean validateFlightIndices(Map<UUID, Integer> flightIndices) {
        if (flightIndices == null || flightIndices.isEmpty()) {
            return true;
        }
        
        // Check for duplicate indices
        List<Integer> indices = flightIndices.values().stream()
                .filter(index -> index != null && index != 0)
                .collect(Collectors.toList());
        
        if (indices.size() != indices.stream().distinct().count()) {
            return false; // Duplicate indices found
        }
        
        // Check for logical sequence
        List<Integer> outboundIndices = indices.stream()
                .filter(index -> index > 0)
                .sorted()
                .collect(Collectors.toList());
        
        List<Integer> returnIndices = indices.stream()
                .filter(index -> index < 0)
                .sorted((a, b) -> Integer.compare(Math.abs(a), Math.abs(b)))
                .collect(Collectors.toList());
        
        // Validate outbound sequence (should be 1, 2, 3, ...)
        for (int i = 0; i < outboundIndices.size(); i++) {
            if (outboundIndices.get(i) != i + 1) {
                return false;
            }
        }
        
        // Validate return sequence (should be -1, -2, -3, ...)
        for (int i = 0; i < returnIndices.size(); i++) {
            if (returnIndices.get(i) != -(i + 1)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Determines the complexity level of a journey
     * 
     * @param analysisResult Flight analysis result
     * @return Complexity level (SIMPLE, MODERATE, COMPLEX)
     */
    public static String determineJourneyComplexity(FlightAnalysisResult analysisResult) {
        if (analysisResult == null) {
            return "UNKNOWN";
        }
        
        int totalSegments = analysisResult.getTotalSegmentCount();
        boolean isRoundTrip = analysisResult.isRoundTrip();
        boolean hasConnecting = analysisResult.isHasConnectingFlights();
        
        if (totalSegments == 1) {
            return "SIMPLE";
        } else if (totalSegments == 2 && isRoundTrip && !hasConnecting) {
            return "SIMPLE";
        } else if (totalSegments <= 4 && hasConnecting) {
            return "MODERATE";
        } else {
            return "COMPLEX";
        }
    }
}
