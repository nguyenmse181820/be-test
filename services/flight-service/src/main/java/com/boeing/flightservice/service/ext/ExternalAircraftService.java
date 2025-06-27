package com.boeing.flightservice.service.ext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.boeing.flightservice.dto.response.FsFlightWithFareDetailsDTO;
import com.boeing.flightservice.exception.BadRequestException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("unchecked")
public class ExternalAircraftService {

    private final WebClient aircraftWebClient;

    public List<String> getSetCodeByAircraft(UUID aircraftId) {
        try {
            var response = aircraftWebClient
                    .get()
                    .uri("/api/v1/public/{id}/active", aircraftId)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                    })
                    .block();

            assert response != null;
            Map<String, Object> data = (Map<String, Object>) response.get("data");
            Map<String, Object> aircraftType = (Map<String, Object>) data.get("aircraftType");
            Map<String, Object> seatMap = (Map<String, Object>) aircraftType.get("seatMap");
            Map<String, Object> layout = (Map<String, Object>) seatMap.get("layout");

            List<String> allSeatCodes = new ArrayList<>();

            for (Map.Entry<String, Object> entry : layout.entrySet()) {
                Map<String, Object> section = (Map<String, Object>) entry.getValue();
                if (section.containsKey("seats")) {
                    List<Map<String, String>> seats = (List<Map<String, String>>) section.get("seats");
                    for (Map<String, String> seat : seats) {
                        allSeatCodes.add(seat.get("seatCode"));
                    }
                }
            }

            log.info("Seats: {}", allSeatCodes);
            return allSeatCodes;
        } catch (Exception e) {
            log.error(e.getMessage());
            e.printStackTrace();
            throw new BadRequestException("Unable to get seat code from aircraft service");
        }
    }

    public FsFlightWithFareDetailsDTO.FsAircraftDTO getAircraftInfo(UUID aircraftId) {
        try {
            Map<String, Object> responseMap = aircraftWebClient.get()
                    .uri("/api/v1/public/{id}/active", aircraftId)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                    })
                    .block();

            assert responseMap != null;
            Map<String, Object> data = (Map<String, Object>) responseMap.get("data");
            Map<String, Object> aircraftType = (Map<String, Object>) data.get("aircraftType");

            return FsFlightWithFareDetailsDTO.FsAircraftDTO.builder()
                    .id(UUID.fromString((String) data.get("id")))
                    .code((String) data.get("code"))
                    .model((String) aircraftType.get("model"))
                    .build();
        } catch (Exception e) {
            log.error(e.getMessage());
            return FsFlightWithFareDetailsDTO.FsAircraftDTO.builder().build();
        }
    }

    public Map<String, List<String>> getAircraftSeatSections(UUID aircraftId) {
        try {
            var response = aircraftWebClient
                    .get()
                    .uri("/api/v1/public/{id}/active", aircraftId)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                    })
                    .block();

            assert response != null;
            Map<String, Object> data = (Map<String, Object>) response.get("data");
            Map<String, Object> aircraftType = (Map<String, Object>) data.get("aircraftType");
            Map<String, Object> seatMap = (Map<String, Object>) aircraftType.get("seatMap");
            Map<String, Object> layout = (Map<String, Object>) seatMap.get("layout");            Map<String, List<String>> seatSections = new java.util.HashMap<>();

            for (Map.Entry<String, Object> entry : layout.entrySet()) {
                String sectionName = entry.getKey(); // e.g., "economy", "business"
                Map<String, Object> section = (Map<String, Object>) entry.getValue();

                // Skip space sections (sections without seats or with type="space")
                if (section.containsKey("type") && "space".equals(section.get("type"))) {
                    continue;
                }

                List<String> sectionSeats = new ArrayList<>();
                if (section.containsKey("seats")) {
                    List<Map<String, String>> seats = (List<Map<String, String>>) section.get("seats");
                    for (Map<String, String> seat : seats) {
                        sectionSeats.add(seat.get("seatCode"));
                    }
                }
                
                // Only add sections that actually have seats
                if (!sectionSeats.isEmpty()) {
                    seatSections.put(sectionName, sectionSeats);
                }
            }

            log.info("Aircraft seat sections: {}", seatSections);
            return seatSections;
        } catch (Exception e) {
            log.error("Error getting aircraft seat sections: {}", e.getMessage());
            throw new BadRequestException("Unable to get seat sections from aircraft service");
        }
    }
}
