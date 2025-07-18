package com.boeing.bookingservice.exception;

/**
 * Custom exception thrown when flight analysis fails due to inability to retrieve flight details.
 * This exception ensures that booking processes fail fast when essential flight data cannot be obtained.
 */
public class FlightAnalysisException extends RuntimeException {
    
    public FlightAnalysisException(String message) {
        super(message);
    }
    
    public FlightAnalysisException(String message, Throwable cause) {
        super(message, cause);
    }
}
