package com.boeing.flightservice.service.ext;

import com.boeing.flightservice.dto.response.FsFlightWithFareDetailsDTO;
import com.boeing.flightservice.entity.enums.FareType;
import com.boeing.flightservice.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("unchecked")
public class ExternalAircraftService {

    private final WebClient aircraftWebClient;

    public List<String> getSetCodeByAircraft(UUID aircraftId) {
        try {
            var layout = getSeatLayout(aircraftId);
            List<String> allSeatCodes = new ArrayList<>();
            for (Map.Entry<String, Object> entry : layout.entrySet()) {
                Map<String, Object> section = (Map<String, Object>) entry.getValue();
                collectSeatsFromSection(section, allSeatCodes);
            }
            log.info("Seats: {}", allSeatCodes);
            return allSeatCodes;
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new BadRequestException("Unable to get seat code from aircraft service");
        }
    }

    public FsFlightWithFareDetailsDTO.FsAircraftDTO getAircraftInfo(UUID aircraftId) {
        try {
            log.debug("Fetching aircraft info for ID: {}", aircraftId);
            
            Map<String, Object> responseMap = aircraftWebClient.get()
                    .uri("/api/v1/public/{id}/active", aircraftId)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                    })
                    .block();

            if (responseMap == null) {
                log.warn("Received null response from aircraft service for ID: {}", aircraftId);
                return FsFlightWithFareDetailsDTO.FsAircraftDTO.builder().build();
            }
            
            Map<String, Object> data = (Map<String, Object>) responseMap.get("data");
            if (data == null) {
                log.warn("No data field in aircraft service response for ID: {}", aircraftId);
                return FsFlightWithFareDetailsDTO.FsAircraftDTO.builder().build();
            }
            
            Map<String, Object> aircraftType = (Map<String, Object>) data.get("aircraftType");
            if (aircraftType == null) {
                log.warn("No aircraft type information available for ID: {}", aircraftId);
                return FsFlightWithFareDetailsDTO.FsAircraftDTO.builder().build();
            }

            return FsFlightWithFareDetailsDTO.FsAircraftDTO.builder()
                    .id(UUID.fromString((String) data.get("id")))
                    .code((String) data.get("code"))
                    .model((String) aircraftType.get("model"))
                    .build();
        } catch (Exception e) {
            log.error("Failed to fetch aircraft info for ID {}: {}", aircraftId, e.getMessage(), e);
            return FsFlightWithFareDetailsDTO.FsAircraftDTO.builder().build();
        }
    }

    public Map<FareType, List<String>> getAircraftSeatSections(UUID aircraftId) {
        try {
            log.debug("Fetching seat sections for aircraft ID: {}", aircraftId);
            
            var layout = getSeatLayout(aircraftId);
            if (layout.isEmpty()) {
                log.warn("No seat layout found for aircraft ID: {}", aircraftId);
                throw new BadRequestException("Aircraft has no seat layout configured");
            }
            
            Map<FareType, List<String>> seatSections = new EnumMap<>(FareType.class);

            for (Map.Entry<String, Object> entry : layout.entrySet()) {
                String sectionName = entry.getKey(); // e.g., "economy", "business"
                Map<String, Object> section = (Map<String, Object>) entry.getValue();

                // Skip "space" sections
                if ("space".equals(section.get("type"))) {
                    continue;
                }

                FareType fareType = mapSectionNameToFareType(sectionName);
                if (fareType == null) {
                    log.debug("Unknown section '{}' ignored for aircraft {}", sectionName, aircraftId);
                    continue; // Unknown section, ignore
                }

                List<String> sectionSeats = new ArrayList<>();
                collectSeatsFromSection(section, sectionSeats);

                if (!sectionSeats.isEmpty()) {
                    seatSections.put(fareType, sectionSeats);
                    log.debug("Found {} seats for {} class in aircraft {}", sectionSeats.size(), fareType, aircraftId);
                }
            }

            if (seatSections.isEmpty()) {
                log.warn("No valid seat sections found for aircraft ID: {}", aircraftId);
                throw new BadRequestException("Aircraft has no valid seat sections configured");
            }

            log.info("Aircraft {} seat sections: {}", aircraftId, seatSections.keySet());
            return seatSections;

        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error getting aircraft seat sections for ID {}: {}", aircraftId, e.getMessage(), e);
            throw new BadRequestException("Unable to get seat sections from aircraft service: " + e.getMessage());
        }
    }
    
    private FareType mapSectionNameToFareType(String sectionName) {
        return switch (sectionName.toLowerCase()) {
            case "business" -> FareType.BUSINESS;
            case "first" -> FareType.FIRST_CLASS;
            case "economy" -> FareType.ECONOMY;
            default -> null;
        };
    }

    private void collectSeatsFromSection(Map<String, Object> section, List<String> sectionSeats) {
        if (section.containsKey("seats")) {
            List<Map<String, String>> seats = (List<Map<String, String>>) section.get("seats");
            for (Map<String, String> seat : seats) {
                sectionSeats.add(seat.get("seatCode"));
            }
        }
    }

    private Map<String, Object> getSeatLayout(UUID aircraftId) {
        try {
            var response = aircraftWebClient
                    .get()
                    .uri("/api/v1/public/{id}/active", aircraftId)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                    })
                    .block();

            if (response == null) {
                log.error("Null response from aircraft service for aircraft ID: {}", aircraftId);
                throw new BadRequestException("Aircraft service returned no data");
            }
            
            Map<String, Object> data = (Map<String, Object>) response.get("data");
            if (data == null) {
                log.error("No data field in aircraft service response for ID: {}", aircraftId);
                throw new BadRequestException("Invalid aircraft service response format");
            }
            
            Map<String, Object> aircraftType = (Map<String, Object>) data.get("aircraftType");
            if (aircraftType == null) {
                log.error("No aircraft type in response for ID: {}", aircraftId);
                throw new BadRequestException("Aircraft type information not available");
            }
            
            Map<String, Object> seatMap = (Map<String, Object>) aircraftType.get("seatMap");
            if (seatMap == null) {
                log.error("No seat map in aircraft type for ID: {}", aircraftId);
                throw new BadRequestException("Aircraft seat map not configured");
            }
            
            Map<String, Object> layout = (Map<String, Object>) seatMap.get("layout");
            if (layout == null) {
                log.error("No layout in seat map for aircraft ID: {}", aircraftId);
                throw new BadRequestException("Aircraft seat layout not configured");
            }
            
            return layout;
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to retrieve seat layout for aircraft {}: {}", aircraftId, e.getMessage(), e);
            throw new BadRequestException("Unable to retrieve aircraft seat layout from external service");
        }
    }
}
