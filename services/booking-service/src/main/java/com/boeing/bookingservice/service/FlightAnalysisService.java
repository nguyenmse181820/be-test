package com.boeing.bookingservice.service;

import com.boeing.bookingservice.dto.response.FlightAnalysisResult;
import com.boeing.bookingservice.model.enums.FlightType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for analyzing flight relationships and determining flight types
 * (round-trip, connecting, etc.) and their proper ordering indices.
 */
public interface FlightAnalysisService {
    
    /**
     * Analyzes a list of flight IDs to determine their relationship and assign indices
     * 
     * @param flightIds List of flight IDs to analyze
     * @return FlightAnalysisResult containing flight indices and metadata
     */
    FlightAnalysisResult analyzeFlightIndices(List<UUID> flightIds);
    
    /**
     * Determines the flight type based on the analysis result
     * 
     * @param analysisResult The result from flight analysis
     * @return FlightType enum value
     */
    FlightType determineFlightType(FlightAnalysisResult analysisResult);
    
    /**
     * Validates that the flight sequence is logical and bookable
     * 
     * @param flightIds List of flight IDs to validate
     * @return true if the sequence is valid, false otherwise
     */
    boolean validateFlightSequence(List<UUID> flightIds);
}
