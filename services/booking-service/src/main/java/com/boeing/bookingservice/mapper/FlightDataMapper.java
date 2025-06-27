package com.boeing.bookingservice.mapper;

import com.boeing.bookingservice.dto.response.*;
import com.boeing.bookingservice.integration.fs.dto.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.Named;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring", imports = {ObjectMapper.class, TypeReference.class, Collections.class, List.class, UUID.class, Collectors.class})
public interface FlightDataMapper {

    // --- Mapping FlightOption ---
    @Mapping(source = "flightId", target = "flightId")
    @Mapping(source = "flightCode", target = "flightCode")
    @Mapping(source = "originAirport", target = "originAirport")
    @Mapping(source = "destinationAirport", target = "destinationAirport")
    @Mapping(source = "departureTime", target = "departureTime")
    @Mapping(source = "arrivalTime", target = "arrivalTime")
    @Mapping(source = "flightDuration", target = "duration")
    @Mapping(source = "aircraftTypeModel", target = "aircraftType")
    @Mapping(source = "availableFares", target = "fareOptions")
    FlightOptionDTO toBpsFlightOptionDTO(FsFlightOptionDTO fisDto);

    List<FlightOptionDTO> toBpsFlightOptionDTOList(List<FsFlightOptionDTO> fisDtoList);

    // FsAirportSummaryDTO -> AirportSummaryDTO (BPS Response)
    AirportSummaryDTO toBpsAirportSummaryDTO(FsAirportSummaryDTO fisAirportSummary);

    // FsFareOptionBasic -> FareOptionSummaryDTO (BPS Response)
    @Mapping(source = "fareId", target = "fareId")
    @Mapping(source = "fareName", target = "name")
    @Mapping(source = "minPrice", target = "price")
    FareOptionSummaryDTO toBpsFareOptionSummaryDTO(FsFareOptionBasic fisFareOptionBasic);


    // --- Mapping FlightWithFareDetails ---
    @Mappings({
            @Mapping(source = "flightId", target = "flightId"),
            @Mapping(source = "flightCode", target = "flightCode"),
            @Mapping(source = "aircraft", target = "aircraft"),
            @Mapping(source = "originAirport", target = "originAirport"),
            @Mapping(source = "destinationAirport", target = "destinationAirport"),
            @Mapping(source = "departureTime", target = "departureTime"),
            @Mapping(source = "estimatedArrivalTime", target = "estimatedArrivalTime"),
            @Mapping(source = "actualArrivalTime", target = "actualArrivalTime"),
            @Mapping(source = "status", target = "status"),
            @Mapping(source = "flightDuration", target = "duration"),
            @Mapping(source = "seatAvailabilityMap", target = "seatMapWithStatus"),
            @Mapping(source = "availableFares", target = "availableFares")
    })
    FlightWithFareDetailsDTO toBpsFlightWithFareDetailsDTO(FsFlightWithFareDetailsDTO fisDto);

    // --- Mapping DTO con FlightWithFareDetails ---
    // FsAircraftDTO -> AircraftInfoDTO (BPS Response)
    @Mapping(source = "type", target = "type")
    AircraftInfoDTO toBpsAircraftDTO(FsAircraftDTO fisAircraft);

    // FsAircraftTypeDTO -> AircraftTypeInfoDTO (BPS Response)
    @Mapping(source = "seatMapLayout", target = "parsedSeatMap")
    AircraftTypeInfoDTO toBpsAircraftTypeDTO(FsAircraftTypeDTO fisAircraftType);

    default List<SeatMapSeatDTO> mapSeatMapLayoutToList(String seatMapLayoutJson) {
        if (seatMapLayoutJson == null || seatMapLayoutJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(seatMapLayoutJson, new TypeReference<List<SeatMapSeatDTO>>() {
            });
        } catch (Exception e) {
            System.err.println("Error parsing seatMapLayout JSON: " + seatMapLayoutJson + " - " + e.getMessage());
            return Collections.emptyList();
        }    }
        
    // FsDetailedFareDTO -> DetailedFareInfoDTO (BPS Response)
    @Mappings({
            @Mapping(source = "flightFareId", target = "flightFareId"),
            @Mapping(source = "name", target = "name"),
            @Mapping(source = "price", target = "price"),
            @Mapping(source = "seatsAvailableForFare", target = "seatsAvailableForFare"),
            @Mapping(source = "baggageAllowance", target = "baggageAllowance"),
            @Mapping(source = "benefits", target = "benefits", qualifiedByName = "mapBenefitUuidsToInfoList")
    })
    DetailedFareInfoDTO toBpsDetailedFareInfoDTO(FsDetailedFareDTO fisDetailedFare);
    
    List<DetailedFareInfoDTO> toBpsDetailedFareInfoDTOList(List<FsDetailedFareDTO> fisDetailedFareList);

    // FsBenefitDTO -> BenefitInfoDTO (BPS Response)
    @Mapping(source = "benefitId", target = "benefitId")
    BenefitInfoDTO toBpsBenefitInfoDTO(FsBenefitDTO fisBenefit);

    List<BenefitInfoDTO> toBpsBenefitInfoDTOList(List<FsBenefitDTO> fisBenefitList);
    @Named("mapBenefitUuidsToInfoList")
    default List<BenefitInfoDTO> mapBenefitUuidsToInfoList(List<UUID> benefitIds) {
        if (benefitIds == null) {
            return Collections.emptyList();
        }
        return benefitIds.stream()
                .map(id -> BenefitInfoDTO.builder()
                        .benefitId(id)
                        .name("Benefit " + id) // Placeholder name since flight service only sends IDs
                        .description("Benefit with ID: " + id)
                        .build())
                .collect(Collectors.toList());
    }
}