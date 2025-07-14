package com.boeing.aircraftservice.initdb;

import com.boeing.aircraftservice.pojos.Aircraft;
import com.boeing.aircraftservice.pojos.AircraftType;
import com.boeing.aircraftservice.repositories.AircraftRepository;
import com.boeing.aircraftservice.repositories.AircraftTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Component
@RequiredArgsConstructor
public class DatabaseInit implements CommandLineRunner {

    private final AircraftTypeRepository aircraftTypeRepository;
    private final AircraftRepository aircraftRepository;    @Override
    public void run(String... args) {
        if (aircraftTypeRepository.count() == 0) {
            initAircraftTypesAndAircrafts();
        }
    }

    private void initAircraftTypesAndAircrafts() {
        // 1. Boeing 777
        AircraftType type1 = createAircraftType(
                "Boeing 777", "Boeing", List.of(
                        new SeatSection("first", "1-1-1", 1, 2),
                        new SpaceSection("space1", "galley", 3, 3),
                        new SeatSection("business", "2-2-2", 4, 6),
                        new SpaceSection("space2", "toilet", 7, 7),
                        new SeatSection("economy", "3-4-3", 8, 12)
                )
        );

        // 2. Airbus A350
        AircraftType type2 = createAircraftType(
                "Airbus A350", "Airbus", List.of(
                        new SeatSection("business", "1-2-1", 1, 3),
                        new SpaceSection("space1", "galley", 4, 4),
                        new SeatSection("economy", "3-3-3", 5, 9)
                )
        );

        // 3. Boeing 737
        AircraftType type3 = createAircraftType(
                "Boeing 737", "Boeing", List.of(
                        new SeatSection("economy", "3-3-3", 1, 6)
                )
        );

        // 4. ATR 72
        AircraftType type4 = createAircraftType(
                "ATR 72", "ATR", List.of(
                        new SeatSection("economy", "2-2-2", 1, 5),
                        new SpaceSection("space1", "galley", 6, 6)
                )
        );

        // 5. Airbus A320
        AircraftType type5 = createAircraftType(
                "Airbus A320", "Airbus", List.of(
                        new SeatSection("economy", "3-3-4", 1, 8)
                )
        );

        // Tạo Aircrafts — trong đó 2 aircraft dùng chung 1 loại (Boeing 777)
        saveAircraft("VN-A777", type1);
        saveAircraft("VN-B777", type1);
        saveAircraft("VN-A350", type2);
        saveAircraft("VN-B737", type3);
        saveAircraft("VN-A320", type5);
    }

    private AircraftType createAircraftType(String model, String manufacturer, List<Section> sections) {
        Map<String, Object> layout = new LinkedHashMap<>();
        int totalSeats = 0;

        for (Section section : sections) {
            if (section instanceof SeatSection seatSection) {
                List<String> columns = getSeatColumns(seatSection.pattern());
                List<Map<String, Object>> seats = new ArrayList<>();
                for (int row = seatSection.fromRow(); row <= seatSection.toRow(); row++) {
                    for (String col : columns) {
                        seats.add(Map.of(
                                "seatCode", row + col
                        ));
                    }
                }

                layout.put(seatSection.name(), Map.of(
                        "pattern", seatSection.pattern(),
                        "fromRow", seatSection.fromRow(),
                        "toRow", seatSection.toRow(),
                        "seats", seats
                ));

                totalSeats += seats.size();
            } else if (section instanceof SpaceSection spaceSection) {
                layout.put(spaceSection.name(), Map.of(
                        "type", "space",
                        "label", spaceSection.label(),
                        "fromRow", spaceSection.fromRow(),
                        "toRow", spaceSection.toRow()
                ));
            }
        }

        AircraftType type = new AircraftType();
        type.setModel(model);
        type.setManufacturer(manufacturer);
        type.setSeatMap(Map.of("layout", layout));
        type.setTotalSeats(totalSeats);
        return aircraftTypeRepository.save(type);
    }

    private void saveAircraft(String code, AircraftType type) {
        Aircraft aircraft = new Aircraft();
        aircraft.setCode(code);
        aircraft.setAircraftType(type);
        aircraftRepository.save(aircraft);
    }

    private List<String> getSeatColumns(String pattern) {
        return switch (pattern) {
            case "1-1-1" -> List.of("A", "D", "G");
            case "2-2-2" -> List.of("A", "B", "D", "E", "G", "H");
            case "3-4-3" -> List.of("A", "B", "C", "D", "E", "F", "G", "H", "J", "K");
            case "1-2-1" -> List.of("A", "D", "G", "K");
            case "3-3" -> List.of("A", "B", "C", "D", "E", "F");
            case "2-2" -> List.of("A", "B", "D", "E");
            case "3-3-3" -> List.of("A", "B", "C", "D", "E", "F", "G", "H", "K");
            default -> List.of("A", "B", "C", "D", "E", "F");
        };
    }

    private interface Section {}
    private record SeatSection(String name, String pattern, int fromRow, int toRow) implements Section {}
    private record SpaceSection(String name, String label, int fromRow, int toRow) implements Section {}
}