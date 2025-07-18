package com.boeing.bookingservice.utils;

import com.boeing.bookingservice.dto.response.FlightDetailDTO;
import com.boeing.bookingservice.integration.fs.dto.FsFlightWithFareDetailsDTO;
import lombok.experimental.UtilityClass;

/**
 * Utility class to convert between flight service DTOs and internal DTOs
 */
@UtilityClass
public class FlightDTOConverter {

    /**
     * Converts FsFlightWithFareDetailsDTO to FlightDetailDTO for flight analysis
     * @param fsFlightDetails Flight details from flight service
     * @return FlightDetailDTO for internal use
     */
    public static FlightDetailDTO convertToFlightDetailDTO(FsFlightWithFareDetailsDTO fsFlightDetails) {
        if (fsFlightDetails == null) {
            return null;
        }
        
        return FlightDetailDTO.builder()
                .id(fsFlightDetails.getFlightId())
                .flightNumber(fsFlightDetails.getFlightCode())
                .departureAirportCode(fsFlightDetails.getOriginAirport() != null ? 
                        fsFlightDetails.getOriginAirport().getCode() : null)
                .arrivalAirportCode(fsFlightDetails.getDestinationAirport() != null ? 
                        fsFlightDetails.getDestinationAirport().getCode() : null)
                .departureTime(fsFlightDetails.getDepartureTime())
                .arrivalTime(fsFlightDetails.getEstimatedArrivalTime())
                .aircraftType(fsFlightDetails.getAircraft() != null ? 
                        fsFlightDetails.getAircraft().getModel() : null)
                .duration(fsFlightDetails.getFlightDurationMinutes())
                .build();
    }
}
