package com.boeing.flightservice.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.http.converter.json.MappingJacksonValue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.boeing.flightservice.dto.paging.FlightDto;
import com.boeing.flightservice.dto.request.FsConfirmSeatsRequestDTO;
import com.boeing.flightservice.dto.request.FsFlightCreateRequestV2;
import com.boeing.flightservice.dto.request.FsReleaseSeatsRequestDTO;
import com.boeing.flightservice.dto.response.AirportResponseDTO;
import com.boeing.flightservice.dto.response.FlightResponseDTO;
import com.boeing.flightservice.dto.response.FsConfirmSeatsResponseDTO;
import com.boeing.flightservice.dto.response.FsFlightWithFareDetailsDTO;
import com.boeing.flightservice.dto.response.FsReleaseSeatsResponseDTO;
import com.boeing.flightservice.dto.response.FsSeatsAvailabilityResponseDTO;
import com.boeing.flightservice.entity.Airport;
import com.boeing.flightservice.entity.Benefit;
import com.boeing.flightservice.entity.Flight;
import com.boeing.flightservice.entity.FlightFare;
import com.boeing.flightservice.entity.Seat;
import com.boeing.flightservice.entity.enums.FlightStatus;
import com.boeing.flightservice.exception.BadRequestException;
import com.boeing.flightservice.repository.AirportRepository;
import com.boeing.flightservice.repository.BenefitRepository;
import com.boeing.flightservice.repository.FlightFareRepository;
import com.boeing.flightservice.repository.FlightRepository;
import com.boeing.flightservice.repository.SeatRepository;
import com.boeing.flightservice.service.cache.SeatPriceCacheService;
import com.boeing.flightservice.service.ext.ExternalAircraftService;
import com.boeing.flightservice.service.spec.FlightService;
import com.boeing.flightservice.service.spec.logic.FlightTimeService;
import com.boeing.flightservice.service.spec.logic.SeatService;
import com.boeing.flightservice.util.PaginationUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class FlightServiceImpl implements FlightService {

    private final SeatPriceCacheService seatPriceCacheService;
    private final ExternalAircraftService externalAircraftService;
    private final SeatService seatService;
    private final FlightTimeService flightTimeService;
    private final FlightRepository flightRepository;
    private final SeatRepository seatRepository;
    private final AirportRepository airportRepository;
    private final BenefitRepository benefitRepository;
    private final FlightFareRepository flightFareRepository;

    @Override
    public MappingJacksonValue findAll(Map<String, String> params) {
        return PaginationUtil.findAll(
                params,
                flightRepository,
                FlightDto.class
        );
    }

    @Override
    public FsSeatsAvailabilityResponseDTO checkSeatAvailability(UUID flightId, List<String> seatCodes) {
        Flight flight = flightRepository.findByIdAndDeleted(flightId, false)
                .orElseThrow(() -> new BadRequestException("Flight not found with ID " + flightId));
        boolean allRequestedSeatsAvailable = true;
        List<FsSeatsAvailabilityResponseDTO.SeatStatus> seatStatuses = new ArrayList<>();

        // Get occupied seat codes
        List<String> occupiedSeatCodes = flight.getOccupiedSeats().stream().map(Seat::getSeatCode).toList();
        List<String> seatInAircraft = externalAircraftService.getSetCodeByAircraft(flight.getAircraftId());

        for (String seat : seatCodes) {
            FsSeatsAvailabilityResponseDTO.SeatStatus seatStatus = FsSeatsAvailabilityResponseDTO.SeatStatus
                    .builder()
                    .seatCode(seat)
                    .available(true)
                    .build();

            if (occupiedSeatCodes.contains(seat) || !seatInAircraft.contains(seat)) {
                seatStatus.setAvailable(false);
                allRequestedSeatsAvailable = false;
            }

            FlightFare fare = seatService.findFareForSeat(seat, flight.getFares());
            Double price = seatService.getSeatPrice(flight, seat);

            seatStatus.setFare(FsSeatsAvailabilityResponseDTO.FareDetail.builder()
                    .id(fare.getId())
                    .name(fare.getName())
                    .price(price)
                    .build());
            seatStatuses.add(seatStatus);
        }

        return FsSeatsAvailabilityResponseDTO.builder()
                .seatStatuses(seatStatuses)
                .allRequestedSeatsAvailable(allRequestedSeatsAvailable)
                .build();
    }

    @Override
    public FsFlightWithFareDetailsDTO getFlightDetails(UUID flightId) {
        Flight flight = flightRepository.findByIdAndDeleted(flightId, false)
                .orElseThrow(() -> new BadRequestException("Flight not found with ID " + flightId));
        FsFlightWithFareDetailsDTO.FsAircraftDTO aircraftDTO = externalAircraftService.getAircraftInfo(flight.getAircraftId());

        int totalSeats = seatService.countTotalSeats(flight.getFares());
        
        // Get only NON-DELETED occupied seats from repository (most accurate source)
        List<Seat> occupiedSeats = seatRepository.findByFlightIdAndDeleted(flightId, false);

        int remainingSeats = totalSeats - occupiedSeats.size();

        return FsFlightWithFareDetailsDTO.builder()
                .flightId(flight.getId())
                .flightCode(flight.getCode())
                .aircraft(aircraftDTO)
                .originAirport(AirportResponseDTO.fromEntity(flight.getOrigin()))
                .destinationAirport(AirportResponseDTO.fromEntity(flight.getDestination()))
                .departureTime(flight.getDepartureTime())
                .estimatedArrivalTime(flight.getEstimatedArrivalTime())
                .status(flight.getStatus())
                .flightDurationMinutes(flight.getFlightDurationMinutes())
                .actualArrivalTime(flight.getEstimatedArrivalTime().plusMinutes(flight.getFlightDurationMinutes().longValue()))
                .occupiedSeats(occupiedSeats.stream().map(Seat::getSeatCode).toList())
                .remainingSeats(remainingSeats)
                .totalSeats(totalSeats)
                .availableFares(flight.getFares().stream().map(
                        fare -> {
                            Double price = seatPriceCacheService.get(flight.getId() + "_" + fare.getId());
                            if (price == null) {
                                price = ThreadLocalRandom.current().nextDouble(fare.getMinPrice(), fare.getMaxPrice());
                                seatPriceCacheService.put(flight.getId() + "_" + fare.getId(), price);
                            }
                            return FsFlightWithFareDetailsDTO.FsDetailedFareDTO
                                    .builder()
                                    .id(fare.getId())
                                    .price(price)
                                    .name(fare.getName())
                                    .seatRange(fare.getSeatRange())
                                    .totalSeats(seatService.countSeatsForFare(fare))
                                    .remainingSeats(seatService.countRemainingSeats(fare, occupiedSeats.stream().map(Seat::getSeatCode).toList()))
                                    .benefits(fare.getBenefits().stream().map(Benefit::getId).toList())
                                    .build();
                        }
                ).toList())
                .build();
    }

    @Override
    @Transactional
    public FsConfirmSeatsResponseDTO confirmSeat(UUID flightId, FsConfirmSeatsRequestDTO request) {
        Flight flight = flightRepository.findByIdAndDeleted(flightId, false)
                .orElseThrow(() -> new BadRequestException("Flight not found with ID " + flightId));
        String status;
        String message;
        List<String> confirmedSeats = new ArrayList<>();
        List<String> failedToConfirmSeats = new ArrayList<>();
        // Get seats from aircraft
        List<String> seatCodes = externalAircraftService.getSetCodeByAircraft(flight.getAircraftId());

        for (String seatCode : request.seatCodes()) {
            Optional<Seat> optionalSeat = seatRepository.findBySeatCodeAndFlightIdAndDeleted(seatCode, flightId, false);
            if (optionalSeat.isPresent() || !seatCodes.contains(seatCode)) {
                failedToConfirmSeats.add(seatCode);
            } else {
                Double price = seatService.getSeatPrice(flight, seatCode);
                Seat seat = Seat.builder()
                        .seatCode(seatCode)
                        .flight(flight)
                        .bookingReference(request.bookingReference())
                        .price(price)
                        .build();
                seatRepository.save(seat);
                confirmedSeats.add(seatCode);
            }
        }

        if (confirmedSeats.isEmpty()) {
            status = "Failed";
            message = "Failed to confirm seat";
        } else if (!failedToConfirmSeats.isEmpty()) {
            status = "Some failed";
            message = "Failed to confirm some seats";
        } else {
            status = "Success";
            message = "Seats confirmed successfully";
        }

        return FsConfirmSeatsResponseDTO.builder()
                .status(status)
                .confirmedSeats(confirmedSeats)
                .failedToConfirmSeats(failedToConfirmSeats)
                .message(message)
                .build();
    }

    @Override
    @Transactional
    public FsReleaseSeatsResponseDTO releaseSeats(UUID flightId, FsReleaseSeatsRequestDTO request) {
        flightRepository.findByIdAndDeleted(flightId, false)
                .orElseThrow(() -> new BadRequestException("Flight not found with ID " + flightId));
        List<Seat> releasedSeats = new ArrayList<>();
        List<String> failedToReleaseSeats = new ArrayList<>();
        String status;

        for (String seatCode : request.seatCodes()) {
            Optional<Seat> optionalSeat = seatRepository.findBySeatCodeAndFlightIdAndDeleted(seatCode, flightId, false);
            if (optionalSeat.isPresent() && optionalSeat.get().getBookingReference().equals(request.bookingReference())) {
                Seat seat = optionalSeat.get();
                seat.setDeleted(true);
                releasedSeats.add(seat);
            } else {
                failedToReleaseSeats.add(seatCode);
            }
        }

        seatRepository.saveAll(releasedSeats);

        if (releasedSeats.isEmpty()) {
            status = "Failed";
        } else if (!failedToReleaseSeats.isEmpty()) {
            status = "Some failed";
        } else {
            status = "Success";
        }

        return FsReleaseSeatsResponseDTO.builder()
                .status(status)
                .releasedSeats(releasedSeats.stream().map(Seat::getSeatCode).toList())
                .failedToReleaseSeats(failedToReleaseSeats)
                .build();
    }

//    @Override
//    @Transactional
//    public FlightResponseDTO createFlight(FsFlightCreateRequest fsFlightCreateRequest) {
//        Airport departure = airportRepository.findByIdAndDeleted(fsFlightCreateRequest.originId(), false)
//                .orElseThrow(() -> new BadRequestException("Airport not found with ID " + fsFlightCreateRequest.originId()));
//
//        Airport destination = airportRepository.findByIdAndDeleted(fsFlightCreateRequest.destinationId(), false)
//                .orElseThrow(() -> new BadRequestException("Destination not found with ID " + fsFlightCreateRequest.destinationId()));
//
//        double calculatedTime = flightTimeService.calculateFlightTime(departure.getLatitude(), departure.getLongitude(), destination.getLatitude(), destination.getLongitude());
//        long calculatedTimeEstimated = ((long) calculatedTime) + 1;
//
//        Flight flight = Flight.builder()
//                .code(fsFlightCreateRequest.code())
//                .aircraftId(fsFlightCreateRequest.aircraftId())
//                .destination(destination)
//                .origin(departure)
//                .departureTime(fsFlightCreateRequest.departureTime())
//                .estimatedArrivalTime(fsFlightCreateRequest.departureTime().plusMinutes(calculatedTimeEstimated))
//                .flightDurationMinutes(calculatedTime)
//                .status(FlightStatus.SCHEDULED_OPEN)
//                .build();
//
//        flight = flightRepository.save(flight);
//
//        List<FlightFare> fares = new ArrayList<>();
//
//        for (var item : fsFlightCreateRequest.fares()) {
//
//            List<Benefit> benefits = new ArrayList<>();
//            for (UUID id : item.benefits()) {
//                Benefit benefit = benefitRepository.findByIdAndDeleted(id, false)
//                        .orElseThrow(() -> new BadRequestException("Benefit not found with ID " + id + " while creating fare " + item.name()));
//                benefits.add(benefit);
//            }
//
//            FlightFare fare = FlightFare.builder()
//                    .minPrice(item.minPrice())
//                    .maxPrice(item.maxPrice())
//                    .name(item.name())
//                    .seatRange(item.seatRange())
//                    .benefits(benefits)
//                    .flight(flight)
//                    .build();
//            fares.add(fare);
//        }
//
//        List<String> seatCodes = externalAircraftService.getSetCodeByAircraft(fsFlightCreateRequest.aircraftId());
//        seatService.validateSeatRanges(fares, seatCodes);
//
//        fares = flightFareRepository.saveAll(fares);
//        flight.setFares(fares);
//        flightRepository.save(flight);
//
//        return FlightResponseDTO.builder()
//                .id(flight.getId())
//                .code(flight.getCode())
//                .origin(flight.getOrigin().getName())
//                .destination(flight.getDestination().getName())
//                .departureTime(flight.getDepartureTime())
//                .estimatedArrivalTime(flight.getEstimatedArrivalTime())
//                .flightDurationMinutes(flight.getFlightDurationMinutes())
//                .status(flight.getStatus())
//                .build();
//    }

    @Override
    @Transactional
    public FlightResponseDTO createFlightV2(FsFlightCreateRequestV2 request) {
        Airport departure = airportRepository.findByIdAndDeleted(request.originId(), false)
                .orElseThrow(() -> new BadRequestException("Airport not found with ID " + request.originId()));

        Airport destination = airportRepository.findByIdAndDeleted(request.destinationId(), false)
                .orElseThrow(() -> new BadRequestException("Destination not found with ID " + request.destinationId()));

        double calculatedTime = flightTimeService.calculateFlightTime(departure.getLatitude(), departure.getLongitude(), destination.getLatitude(), destination.getLongitude());
        long calculatedTimeEstimated = ((long) calculatedTime) + 1;

        Flight flight = Flight.builder()
                .code(request.code())
                .aircraftId(request.aircraftId())
                .destination(destination)
                .origin(departure)
                .departureTime(request.departureTime())
                .estimatedArrivalTime(request.departureTime().plusMinutes(calculatedTimeEstimated))
                .flightDurationMinutes(calculatedTime)
                .status(FlightStatus.SCHEDULED_OPEN)
                .build();

        flight = flightRepository.save(flight);

        // Get aircraft seat sections from aircraft service
        Map<String, List<String>> aircraftSeatSections = externalAircraftService.getAircraftSeatSections(request.aircraftId());

        List<FlightFare> fares = new ArrayList<>();

        for (var seatClassFare : request.seatClassFares()) {
            // Validate that the seat class exists in aircraft
            if (!aircraftSeatSections.containsKey(seatClassFare.seatClassName())) {
                throw new BadRequestException("Seat class '" + seatClassFare.seatClassName() + "' not found in aircraft");
            }

            List<Benefit> benefits = new ArrayList<>();
            for (UUID id : seatClassFare.benefits()) {
                Benefit benefit = benefitRepository.findByIdAndDeleted(id, false)
                        .orElseThrow(() -> new BadRequestException("Benefit not found with ID " + id + " while creating fare " + seatClassFare.name()));
                benefits.add(benefit);
            }

            // Convert seat codes list to seat range string format
            List<String> seatCodes = aircraftSeatSections.get(seatClassFare.seatClassName());
            String seatRange = convertSeatCodesToRange(seatCodes);

            FlightFare fare = FlightFare.builder()
                    .minPrice(seatClassFare.minPrice())
                    .maxPrice(seatClassFare.maxPrice())
                    .name(seatClassFare.name())
                    .seatRange(seatRange)
                    .benefits(benefits)
                    .flight(flight)
                    .build();
            fares.add(fare);
        }

        // Get all seat codes for validation
        List<String> allSeatCodes = externalAircraftService.getSetCodeByAircraft(request.aircraftId());
        seatService.validateSeatRanges(fares, allSeatCodes);

        fares = flightFareRepository.saveAll(fares);
        flight.setFares(fares);
        flightRepository.save(flight);

        return FlightResponseDTO.builder()
                .id(flight.getId())
                .code(flight.getCode())
                .origin(flight.getOrigin().getName())
                .destination(flight.getDestination().getName())
                .departureTime(flight.getDepartureTime())
                .estimatedArrivalTime(flight.getEstimatedArrivalTime())
                .flightDurationMinutes(flight.getFlightDurationMinutes())
                .status(flight.getStatus())
                .build();
    }

    @Override
    public Map<String, List<String>> getAircraftSeatSections(UUID aircraftId) {
        return externalAircraftService.getAircraftSeatSections(aircraftId);
    }

    @Override
    public int getAvailableSeatsCount(UUID flightId, String fareClass) {
        Flight flight = flightRepository.findByIdAndDeleted(flightId, false)
                .orElseThrow(() -> new BadRequestException("Flight not found with ID " + flightId));

        List<Seat> occupiedSeats = seatRepository.findByFlightIdAndDeleted(flightId, false);
        List<String> occupiedSeatCodes = occupiedSeats.stream().map(Seat::getSeatCode).toList();

        FlightFare targetFare = flight.getFares().stream()
                .filter(fare -> fare.getName().equalsIgnoreCase(fareClass))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Fare class '" + fareClass + "' not found for flight " + flightId));

        int remainingSeats = seatService.countRemainingSeats(targetFare, occupiedSeatCodes);
        
        return Math.max(0, remainingSeats);
    }

    private String convertSeatCodesToRange(List<String> seatCodes) {
        if (seatCodes == null || seatCodes.isEmpty()) {
            return "";
        }
        return String.join(",", seatCodes);
    }
}