package com.boeing.flightservice.service.impl;

import com.boeing.flightservice.dto.paging.FlightDto;
import com.boeing.flightservice.dto.request.*;
import com.boeing.flightservice.dto.response.*;
import com.boeing.flightservice.dto.union.Search;
import com.boeing.flightservice.entity.*;
import com.boeing.flightservice.entity.enums.FareType;
import com.boeing.flightservice.entity.enums.FlightStatus;
import com.boeing.flightservice.exception.BadRequestException;
import com.boeing.flightservice.repository.*;
import com.boeing.flightservice.service.cache.SeatPriceCacheService;
import com.boeing.flightservice.service.ext.ExternalAircraftService;
import com.boeing.flightservice.service.spec.FlightService;
import com.boeing.flightservice.service.spec.logic.SeatService;
import com.boeing.flightservice.util.PaginationUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.converter.json.MappingJacksonValue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class FlightServiceImpl implements FlightService {

    private final SeatPriceCacheService seatPriceCacheService;
    private final ExternalAircraftService externalAircraftService;
    private final SeatService seatService;
    private final FlightRepository flightRepository;
    private final SeatRepository seatRepository;
    private final BenefitRepository benefitRepository;
    private final FlightFareRepository flightFareRepository;
    private final RouteRepository routeRepository;

    @Value("${business.default-carry-on-weight}")
    private int defaultCarryOnWeight;

    @Value("${business.default-checked-baggage-weight}")
    private int defaultCheckedBaggageWeight;

    @Value("${business.minimum-hours-between-flights-of-aircraft}")
    private int minimumHoursBetweenFlightsOfAircraft;

    @Override
    public MappingJacksonValue findAll(Map<String, String> params) {
        return PaginationUtil.findAll(
                params,
                flightRepository,
                FlightDto.class);
    }

    @Override
    public FsSeatsAvailabilityResponseDTO checkSeatAvailability(UUID flightId, List<String> seatCodes) {
        // Input validation
        if (seatCodes == null || seatCodes.isEmpty()) {
            throw new BadRequestException("Seat codes list cannot be empty");
        }

        if (seatCodes.size() > 10) {
            throw new BadRequestException("Cannot check more than 10 seats at once");
        }

        Flight flight = flightRepository.findByIdAndDeleted(flightId, false)
                .orElseThrow(() -> new BadRequestException("Flight not found with ID " + flightId));

        boolean allRequestedSeatsAvailable = true;
        List<FsSeatsAvailabilityResponseDTO.SeatStatus> seatStatuses = new ArrayList<>();

        // Get occupied seat codes more efficiently
        List<String> occupiedSeatCodes = seatRepository.findByFlightIdAndDeleted(flightId, false)
                .stream()
                .map(Seat::getSeatCode)
                .toList();

        // Get valid aircraft seats with caching consideration
        List<String> seatInAircraft = externalAircraftService.getSetCodeByAircraft(flight.getAircraftId());

        log.debug("Flight {} has {} occupied seats: {}", flightId, occupiedSeatCodes.size(), occupiedSeatCodes);
        log.debug("Aircraft has {} total seats available", seatInAircraft.size());

        // Track unavailable seats for detailed logging
        List<String> unavailableSeats = new ArrayList<>();

        for (String seat : seatCodes) {
            // Validate seat code format
            if (!seat.matches("\\d+[A-Z]")) {
                throw new BadRequestException("Invalid seat code format: " + seat);
            }

            FsSeatsAvailabilityResponseDTO.SeatStatus seatStatus = FsSeatsAvailabilityResponseDTO.SeatStatus
                    .builder()
                    .seatCode(seat)
                    .available(true)
                    .build();

            // Check availability logic
            boolean isOccupied = occupiedSeatCodes.contains(seat);
            boolean isValidSeat = seatInAircraft.contains(seat);

            if (isOccupied || !isValidSeat) {
                seatStatus.setAvailable(false);
                allRequestedSeatsAvailable = false;
                unavailableSeats.add(seat);

                log.debug("Seat {} unavailable - occupied: {}, valid: {}", seat, isOccupied, isValidSeat);
            }

            // Get fare pricing information
            try {
                SeatService.FarePrice farePrice = seatService.getSeatFareAndPrice(flight, seat);
                seatStatus.setFare(FsSeatsAvailabilityResponseDTO.FareDetail.builder()
                        .id(farePrice.fare().getId())
                        .name(farePrice.fare().getName())
                        .price(farePrice.price())
                        .build());
            } catch (Exception e) {
                log.warn("Could not get fare information for seat {}: {}", seat, e.getMessage());
                // Continue without fare info rather than failing
            }

            seatStatuses.add(seatStatus);
        }

        log.info("Seat availability check completed for flight {}: {} out of {} seats available. Unavailable seats: {}",
                flightId, (seatCodes.size() - unavailableSeats.size()), seatCodes.size(), unavailableSeats);

        return FsSeatsAvailabilityResponseDTO.builder()
                .seatStatuses(seatStatuses)
                .allRequestedSeatsAvailable(allRequestedSeatsAvailable)
                .build();
    }

    @Override
    public FsFlightWithFareDetailsDTO getFlightDetails(UUID flightId) {
        Flight flight = flightRepository.findByIdAndDeleted(flightId, false)
                .orElseThrow(() -> new BadRequestException("Flight not found with ID " + flightId));
        return getFlightDetails(flight);
    }

    private FsFlightWithFareDetailsDTO getFlightDetails(Flight flight) {
        FsFlightWithFareDetailsDTO.FsAircraftDTO aircraftDTO = externalAircraftService
                .getAircraftInfo(flight.getAircraftId());
        int totalSeats = 0;
        if (flight.getFares() != null) {
            for (FlightFare fare : flight.getFares()) {
                if (fare.getSeats() != null && !fare.getSeats().isEmpty()) {
                    totalSeats += fare.getSeats().split(",").length;
                }
            }
        }
        List<Seat> occupiedSeats = seatRepository.findByFlightIdAndDeleted(flight.getId(), false);

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
                .actualArrivalTime(
                        flight.getEstimatedArrivalTime().plusMinutes(flight.getFlightDurationMinutes().longValue()))
                .occupiedSeats(occupiedSeats.stream().map(Seat::getSeatCode).toList())
                .remainingSeats(remainingSeats)
                .totalSeats(totalSeats)
                .carryOnLuggageWeight(defaultCarryOnWeight)
                .checkedBaggageWeight(defaultCheckedBaggageWeight)
                .availableFares(flight.getFares() != null ? flight.getFares().stream().map(
                        fare -> {
                            Double price = seatPriceCacheService.get(fare.getId().toString());
                            if (price == null) {
                                // Handle case where minPrice equals maxPrice to avoid IllegalArgumentException
                                if (fare.getMinPrice().equals(fare.getMaxPrice())) {
                                    price = fare.getMinPrice();
                                } else {
                                    price = ThreadLocalRandom.current().nextDouble(fare.getMinPrice(), fare.getMaxPrice());
                                }
                                seatPriceCacheService.put(fare.getId().toString(), price);
                            }
                            List<String> seats = Arrays.stream(fare.getSeats().split(",")).toList();
                            return FsFlightWithFareDetailsDTO.FsDetailedFareDTO
                                    .builder()
                                    .id(fare.getId())
                                    .fareType(fare.getFareType())
                                    .price(price)
                                    .name(fare.getName())
                                    .seats(seats)
                                    .totalSeats(seats.size())
                                    .occupiedSeats(occupiedSeats.stream().map(Seat::getSeatCode).toList())
                                    .benefits(
                                            fare.getBenefits().stream()
                                                    .map(b -> FsFlightWithFareDetailsDTO.Benefit.builder()
                                                            .id(b.getId())
                                                            .name(b.getName())
                                                            .description(b.getDescription())
                                                            .iconURL(b.getIconURL())
                                                            .build())
                                                    .toList())
                                    .build();
                        }).toList() : List.of())
                .build();
    }

    @Override
    @Transactional
    public FsConfirmSeatsResponseDTO confirmSeat(UUID flightId, FsConfirmSeatsRequestDTO request) {
        log.info("=== CONFIRM SEATS REQUEST ===");
        log.info("Flight ID: {}", flightId);
        log.info("Booking Reference: {}", request.bookingReference());
        log.info("Seat Codes to confirm: {}", request.seatCodes());

        Flight flight = flightRepository.findByIdAndDeleted(flightId, false)
                .orElseThrow(() -> new BadRequestException("Flight not found with ID " + flightId));
        String status;
        String message;
        List<String> confirmedSeats = new ArrayList<>();
        List<String> failedToConfirmSeats = new ArrayList<>();
        List<String> seatCodes = externalAircraftService.getSetCodeByAircraft(flight.getAircraftId());

        log.info("Valid seat codes for aircraft {}: {}", flight.getAircraftId(), seatCodes);

        for (String seatCode : request.seatCodes()) {
            log.info("Processing seat: {}", seatCode);

            Optional<Seat> optionalSeat = seatRepository.findBySeatCodeAndFlightIdAndDeleted(seatCode, flightId, false);
            if (optionalSeat.isPresent()) {
                log.warn("❌ Seat {} is already occupied! Existing booking: {}",
                        seatCode, optionalSeat.get().getBookingReference());
                failedToConfirmSeats.add(seatCode);
            } else if (!seatCodes.contains(seatCode)) {
                log.warn("❌ Seat {} is not valid for this aircraft!", seatCode);
                failedToConfirmSeats.add(seatCode);
            } else {
                try {
                    SeatService.FarePrice farePrice = seatService.getSeatFareAndPrice(flight, seatCode);
                    log.info("Got fare and price for seat {}: fare={}, price={}",
                            seatCode, farePrice.fare().getId(), farePrice.price());

                    Seat seat = Seat.builder()
                            .seatCode(seatCode)
                            .flight(flight)
                            .bookingReference(request.bookingReference())
                            .price(farePrice.price())
                            .flightFare(farePrice.fare())
                            .build();

                    log.info("Built seat entity: seatCode={}, flightId={}, bookingRef={}, deleted={}",
                            seat.getSeatCode(), seat.getFlight().getId(), seat.getBookingReference(),
                            seat.getDeleted());

                    // Save and immediately verify
                    Seat savedSeat = seatRepository.save(seat);
                    log.info("💾 SAVED seat to database - ID: {}, seatCode: {}, deleted: {}",
                            savedSeat.getId(), savedSeat.getSeatCode(), savedSeat.getDeleted());

                    // Immediate verification - check if seat was actually saved
                    Optional<Seat> verificationSeat = seatRepository.findBySeatCodeAndFlightIdAndDeleted(seatCode,
                            flightId, false);
                    if (verificationSeat.isPresent()) {
                        log.info("✅ VERIFICATION SUCCESS: Seat {} successfully saved and retrievable from database",
                                seatCode);
                    } else {
                        log.error("❌ VERIFICATION FAILED: Seat {} was NOT found in database after save!", seatCode);
                    }

                    log.info("✅ Successfully confirmed seat {} for booking {} with price {}",
                            seatCode, request.bookingReference(), farePrice.price());
                    confirmedSeats.add(seatCode);

                } catch (Exception e) {
                    log.error("❌ Exception while confirming seat {}: {}", seatCode, e.getMessage(), e);
                    failedToConfirmSeats.add(seatCode);
                }
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

        log.info("=== CONFIRM SEATS RESULT ===");
        log.info("Status: {}", status);
        log.info("Confirmed seats: {}", confirmedSeats);
        log.info("Failed seats: {}", failedToConfirmSeats);
        log.info("=== END CONFIRM SEATS ===");

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
            if (optionalSeat.isPresent()
                    && optionalSeat.get().getBookingReference().equals(request.bookingReference())) {
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

    @Override
    @Transactional
    public FlightResponseDTO createFlight(FsFlightCreateRequest request) {
        // Input validation
        validateFlightCreateRequest(request);
        
        Route route = routeRepository.findByIdAndDeleted(request.routeId(), false)
                .orElseThrow(() -> new BadRequestException("Route not found with ID " + request.routeId()));

        // Validate route is active
        validateRouteOperational(route);
        
        // Validate aircraft exists and is available
        validateAircraftAvailability(request.aircraftId());
        
        Flight flight = Flight.builder()
                .code(request.code())
                .aircraftId(request.aircraftId())
                .destination(route.getDestination())
                .origin(route.getOrigin())
                .departureTime(request.departureTime())
                .estimatedArrivalTime(request.departureTime().plusMinutes(route.getEstimatedDurationMinutes()))
                .flightDurationMinutes(route.getEstimatedDurationMinutes())
                .status(FlightStatus.SCHEDULED_OPEN)
                .build();

        validateConflictingSchedule(flight);

        flight = flightRepository.save(flight);

        try {
            // Get aircraft seat sections from aircraft service with error handling
            Map<FareType, List<String>> aircraftSeatSections = getAircraftSeatSectionsWithErrorHandling(request.aircraftId());

            List<FlightFare> fares = new ArrayList<>();

            // Validate fare type uniqueness
            validateFareTypeUniqueness(request.seatClassFares());
            
            for (var seatClassFare : request.seatClassFares()) {
                if (!aircraftSeatSections.containsKey(seatClassFare.fareType())) {
                    throw new BadRequestException(
                            "Seat class '" + seatClassFare.fareType().name() + "' not found in aircraft");
                }


                List<Benefit> benefits = validateAndRetrieveBenefits(seatClassFare.benefits(), seatClassFare.name());

                List<String> seatCodes = aircraftSeatSections.get(seatClassFare.fareType());

                // Validate seat codes are not empty
                if (seatCodes.isEmpty()) {
                    throw new BadRequestException(
                            "No seats available for fare type: " + seatClassFare.fareType().name());
                }

                FlightFare fare = FlightFare.builder()
                        .minPrice(seatClassFare.minPrice())
                        .maxPrice(seatClassFare.maxPrice())
                        .name(seatClassFare.name())
                        .seats(String.join(",", seatCodes))
                        .fareType(seatClassFare.fareType())
                        .benefits(benefits)
                        .flight(flight)
                        .build();
                fares.add(fare);
            }

            // Save all fares in batch
            fares = flightFareRepository.saveAll(fares);
            flight.setFares(fares);
            flight = flightRepository.save(flight);
            
            log.info("Successfully created flight {} with {} fare types", flight.getCode(), fares.size());
            
        } catch (BadRequestException e) {
            log.error("Validation error during flight creation: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during flight creation for code {}: {}", request.code(), e.getMessage(), e);
            throw new BadRequestException("Failed to create flight due to internal error: " + e.getMessage());
        }

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
    @Transactional
    public FlightResponseDTO updateFlight(UUID flightId, FsFlightCreateRequest request) {
        // Find existing flight
        Flight existingFlight = flightRepository.findByIdAndDeleted(flightId, false)
                .orElseThrow(() -> new BadRequestException("Flight not found with ID " + flightId));

        // Validate input
        validateFlightUpdateRequest(request, flightId);
        
        Route route = routeRepository.findByIdAndDeleted(request.routeId(), false)
                .orElseThrow(() -> new BadRequestException("Route not found with ID " + request.routeId()));

        // Validate route is operational
        validateRouteOperational(route);
        
        // Validate aircraft exists and is available
        validateAircraftAvailability(request.aircraftId());
        
        // Update flight details
        existingFlight.setCode(request.code());
        existingFlight.setAircraftId(request.aircraftId());
        existingFlight.setDestination(route.getDestination());
        existingFlight.setOrigin(route.getOrigin());
        existingFlight.setDepartureTime(request.departureTime());
        existingFlight.setEstimatedArrivalTime(request.departureTime().plusMinutes(route.getEstimatedDurationMinutes()));
        existingFlight.setFlightDurationMinutes(route.getEstimatedDurationMinutes());

        // Validate schedule conflicts (excluding current flight)
        validateConflictingSchedule(existingFlight);

        // Update flight
        existingFlight = flightRepository.save(existingFlight);

        try {
            // Get aircraft seat sections from aircraft service with error handling
            Map<FareType, List<String>> aircraftSeatSections = getAircraftSeatSectionsWithErrorHandling(request.aircraftId());

            // Delete existing fares
            if (existingFlight.getFares() != null) {
                flightFareRepository.deleteAll(existingFlight.getFares());
            }

            List<FlightFare> fares = new ArrayList<>();

            // Validate fare type uniqueness
            validateFareTypeUniqueness(request.seatClassFares());
            
            for (var seatClassFare : request.seatClassFares()) {
                if (!aircraftSeatSections.containsKey(seatClassFare.fareType())) {
                    throw new BadRequestException(
                            "Seat class '" + seatClassFare.fareType().name() + "' not found in aircraft");
                }

                List<Benefit> benefits = validateAndRetrieveBenefits(seatClassFare.benefits(), seatClassFare.name());

                List<String> seatCodes = aircraftSeatSections.get(seatClassFare.fareType());

                // Validate seat codes are not empty
                if (seatCodes.isEmpty()) {
                    throw new BadRequestException(
                            "No seats available for fare type: " + seatClassFare.fareType().name());
                }

                FlightFare fare = FlightFare.builder()
                        .minPrice(seatClassFare.minPrice())
                        .maxPrice(seatClassFare.maxPrice())
                        .name(seatClassFare.name())
                        .seats(String.join(",", seatCodes))
                        .fareType(seatClassFare.fareType())
                        .benefits(benefits)
                        .flight(existingFlight)
                        .build();
                fares.add(fare);
            }

            // Save all fares in batch
            fares = flightFareRepository.saveAll(fares);
            existingFlight.setFares(fares);
            existingFlight = flightRepository.save(existingFlight);
            
            log.info("Successfully updated flight {} with {} fare types", existingFlight.getCode(), fares.size());
            
        } catch (BadRequestException e) {
            log.error("Validation error during flight update: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during flight update for ID {}: {}", flightId, e.getMessage(), e);
            throw new BadRequestException("Failed to update flight due to internal error: " + e.getMessage());
        }

        return FlightResponseDTO.builder()
                .id(existingFlight.getId())
                .code(existingFlight.getCode())
                .origin(existingFlight.getOrigin().getName())
                .destination(existingFlight.getDestination().getName())
                .departureTime(existingFlight.getDepartureTime())
                .estimatedArrivalTime(existingFlight.getEstimatedArrivalTime())
                .flightDurationMinutes(existingFlight.getFlightDurationMinutes())
                .status(existingFlight.getStatus())
                .build();
    }

    @Override
    public FlightResponseDTO getFlightById(UUID flightId) {
        Flight flight = flightRepository.findByIdAndDeleted(flightId, false)
                .orElseThrow(() -> new BadRequestException("Flight not found with ID " + flightId));

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

    private void validateConflictingSchedule(Flight flight) {
        // Validate aircraft scheduling conflicts
        validateAircraftScheduleConflicts(flight);
        
    }
    
    private void validateAircraftScheduleConflicts(Flight flight) {
        List<Flight> conflictingFlights = flightRepository
                .findByAircraftIdAndDeleted(flight.getAircraftId(), false)
                .stream()
                .filter(f -> !f.getId().equals(flight.getId())) // Exclude current flight if updating
                .filter(f -> {
                    LocalDateTime minBuffer = f.getDepartureTime().minusHours(minimumHoursBetweenFlightsOfAircraft);
                    LocalDateTime maxBuffer = f.getEstimatedArrivalTime().plusHours(minimumHoursBetweenFlightsOfAircraft);

                    return !flight.getEstimatedArrivalTime().isBefore(minBuffer)
                            && !flight.getDepartureTime().isAfter(maxBuffer);
                })
                .toList();
        if (!conflictingFlights.isEmpty()) {
            List<String> conflictingFlightCodes = conflictingFlights.stream()
                    .map(Flight::getCode)
                    .toList();
            throw new BadRequestException("Flight schedule conflicts with existing flights: "
                    + String.join(", ", conflictingFlightCodes));
        }
    }
    
    

    @Override
    @Deprecated
    public Map<FareType, List<String>> getAircraftSeatSections(UUID aircraftId) {
        return externalAircraftService.getAircraftSeatSections(aircraftId);
    }

    @Override
    @Deprecated
    public int getAvailableSeatsCount(UUID flightId, String fareClass) {
        Flight flight = flightRepository.findByIdAndDeleted(flightId, false)
                .orElseThrow(() -> new BadRequestException("Flight not found with ID " + flightId));

        List<Seat> occupiedSeats = seatRepository.findByFlightIdAndDeleted(flightId, false);
        List<String> occupiedSeatCodes = occupiedSeats.stream().map(Seat::getSeatCode).toList();

        FlightFare targetFare = flight.getFares().stream()
                .filter(fare -> fare.getName().equalsIgnoreCase(fareClass))
                .findFirst()
                .orElseThrow(() -> new BadRequestException(
                        "Fare class '" + fareClass + "' not found for flight " + flightId));

        int remainingSeats = seatService.countRemainingSeats(targetFare, occupiedSeatCodes);

        return Math.max(0, remainingSeats);
    }

    @Override
    public Search.Response searchFlights(Search.Request request) {
        Route route = routeRepository.findByIdAndDeleted(request.routeId(), false)
                .orElseThrow(() -> new BadRequestException("Invalid route ID: " + request.routeId()));

        // Default to 1 adult if not specified
        Integer adultsObj = request.noAdults();
        int adults = (adultsObj != null && adultsObj > 0) ? adultsObj : 1;

        // Default to 0 children if not specified
        Integer childrenObj = request.noChildren();
        int children = (childrenObj != null && childrenObj > 0) ? childrenObj : 0;

        // Default to 0 babies if not specified
        // Babies travel for free and don't need seats, so we just log this for
        // information
        Integer babiesObj = request.noBabies();
        if (babiesObj != null && babiesObj > 0) {
            log.info("Flight search includes {} babies (travel for free, no seats required)", babiesObj);
        }

        // Calculate total required seats (adults + children, babies don't need seats)
        int requiredSeats = adults + children;

        // Find direct flights
        List<Flight> directFlights = flightRepository
                .findByDepartureTimeGreaterThanEqualAndStatusAndDeletedAndDestinationAndOrigin(
                        request.departureDate().atStartOfDay(),
                        FlightStatus.SCHEDULED_OPEN,
                        false,
                        route.getDestination(),
                        route.getOrigin());

        // Filter flights that have enough available seats
        List<Flight> availableDirectFlights = directFlights.stream()
                .filter(flight -> hasEnoughAvailableSeats(flight, requiredSeats))
                .toList();

        List<FsFlightWithFareDetailsDTO> directs = availableDirectFlights.stream()
                .map(this::getFlightDetails)
                .toList();

        // Find connecting flights (with one stop)
        List<List<FsFlightWithFareDetailsDTO>> connects = findConnectingFlights(
                request.departureDate(),
                route.getOrigin(),
                route.getDestination(),
                requiredSeats);

        int total = directs.size() + connects.size();

        return Search.Response.builder()
                .total(total)
                .directs(directs)
                .connects(connects)
                .build();
    }

    private List<List<FsFlightWithFareDetailsDTO>> findConnectingFlights(
            LocalDate departureDate,
            Airport origin,
            Airport destination,
            int requiredSeats) {
        // This is the max allowed layover time in hours (24 hours)
        final int MAX_LAYOVER_HOURS = 24;
        // This is the minimum layover time in minutes (1 hour)
        final int MIN_LAYOVER_MINUTES = 60;

        // Step 1: Find all potential first leg flights departing from the origin
        List<Flight> firstLegFlights = flightRepository.findByDepartureTimeGreaterThanEqualAndStatusAndDeletedAndOrigin(
                departureDate.atStartOfDay(),
                FlightStatus.SCHEDULED_OPEN,
                false,
                origin);

        // Filter first leg flights that have enough available seats
        List<Flight> availableFirstLegFlights = firstLegFlights.stream()
                .filter(flight -> hasEnoughAvailableSeats(flight, requiredSeats))
                .toList();

        // Step 2: Find potential second leg flights for each first leg flight
        List<List<FsFlightWithFareDetailsDTO>> connectingFlights = new ArrayList<>();

        for (Flight firstLeg : availableFirstLegFlights) {
            // Skip if this first leg already reaches the destination directly
            if (firstLeg.getDestination().getId().equals(destination.getId())) {
                continue;
            }

            // Calculate earliest and latest allowed departure time for second leg
            LocalDateTime earliestSecondLegDeparture = firstLeg.getEstimatedArrivalTime()
                    .plusMinutes(MIN_LAYOVER_MINUTES);
            LocalDateTime latestSecondLegDeparture = firstLeg.getEstimatedArrivalTime().plusHours(MAX_LAYOVER_HOURS);

            // Find connecting flights from the layover airport to the final destination
            List<Flight> secondLegFlights = flightRepository
                    .findByDepartureTimeBetweenAndStatusAndDeletedAndOriginAndDestination(
                            earliestSecondLegDeparture,
                            latestSecondLegDeparture,
                            FlightStatus.SCHEDULED_OPEN,
                            false,
                            firstLeg.getDestination(), // Layover airport (destination of first leg)
                            destination // Final destination
                    );

            // Filter second leg flights that have enough available seats
            List<Flight> availableSecondLegFlights = secondLegFlights.stream()
                    .filter(flight -> hasEnoughAvailableSeats(flight, requiredSeats))
                    .toList();

            // Add valid connecting flight pairs to results
            for (Flight secondLeg : availableSecondLegFlights) {
                List<FsFlightWithFareDetailsDTO> connectionPair = new ArrayList<>();
                connectionPair.add(getFlightDetails(firstLeg));
                connectionPair.add(getFlightDetails(secondLeg));
                connectingFlights.add(connectionPair);
            }
        }

        return connectingFlights;
    }

    private int getRemainingSeats(Flight flight) {
        int totalSeats = 0;
        // Handle case where flight might not have fares configured
        if (flight.getFares() != null) {
            for (FlightFare fare : flight.getFares()) {
                if (fare.getSeats() != null && !fare.getSeats().isEmpty()) {
                    totalSeats += fare.getSeats().split(",").length;
                }
            }
        }
        List<Seat> occupiedSeats = seatRepository.findByFlightIdAndDeleted(flight.getId(), false);
        return totalSeats - occupiedSeats.size();
    }

    private boolean hasEnoughAvailableSeats(Flight flight, int requiredSeats) {
        return getRemainingSeats(flight) >= requiredSeats;
    }

    @Override
    @Transactional
    @Deprecated
    // Will fix if needed in the future
    public FsConfirmFareSaleResponseDTO confirmFareSale(UUID flightId, String fareName,
                                                        FsConfirmFareSaleRequestDTO request) {
        // Validate flight exists
        flightRepository.findByIdAndDeleted(flightId, false)
                .orElseThrow(() -> new BadRequestException("Flight not found with ID " + flightId));

        // Find the fare by flight ID and fare name
        FlightFare fare = flightFareRepository.findByFlightIdAndFareNameAndDeleted(flightId, fareName)
                .orElseThrow(() -> new BadRequestException("Fare '" + fareName + "' not found for flight " + flightId));

        try {
            /*
             * Old logic for calculating available seats
             * int totalSeats = fare.getTotalSeats() != null ? fare.getTotalSeats() :
             * seatService.countSeatsForFare(fare);
             * int currentSoldSeats = fare.getSoldSeats() != null ? fare.getSoldSeats() : 0;
             * int availableSeats = totalSeats - currentSoldSeats;
             */
            int availableSeats = 0;

            // Check if enough seats are available
            if (availableSeats < request.getSoldCount()) {
                return FsConfirmFareSaleResponseDTO.builder()
                        .success(false)
                        .fareName(fareName)
                        .confirmedCount(0)
                        .failureReason("Not enough available seats. Available: " + availableSeats + ", Requested: "
                                + request.getSoldCount())
                        .build();
            }

            /*
             * Update sold seats count (old logic)
             * fare.setSoldSeats(currentSoldSeats + request.getSoldCount());
             */

            /*
             * Set total seats if not already set (old logic)
             * if (fare.getTotalSeats() == null) {
             * fare.setTotalSeats(totalSeats);
             * }
             */

            flightFareRepository.save(fare);

            return FsConfirmFareSaleResponseDTO.builder()
                    .success(true)
                    .fareName(fareName)
                    .confirmedCount(request.getSoldCount())
                    .failureReason(null)
                    .build();

        } catch (Exception e) {
            log.error("Error confirming fare sale for flight {} and fare {}: {}", flightId, fareName, e.getMessage());
            return FsConfirmFareSaleResponseDTO.builder()
                    .success(false)
                    .fareName(fareName)
                    .confirmedCount(0)
                    .failureReason("Internal error: " + e.getMessage())
                    .build();
        }
    }

    @Override
    @Transactional
    @Deprecated
    public FsReleaseFareResponseDTO releaseFare(UUID flightId, String fareName, FsReleaseFareRequestDTO request) {
        // Validate flight exists
        flightRepository.findByIdAndDeleted(flightId, false)
                .orElseThrow(() -> new BadRequestException("Flight not found with ID " + flightId));

        // Find the fare by flight ID and fare name
        FlightFare fare = flightFareRepository.findByFlightIdAndFareNameAndDeleted(flightId, fareName)
                .orElseThrow(() -> new BadRequestException("Fare '" + fareName + "' not found for flight " + flightId));
        try {
            /*
             * Old logic for calculating available seats
             * int currentSoldSeats = fare.getSoldSeats() != null ? fare.getSoldSeats() : 0;
             */
            int currentSoldSeats = 0;
            Integer countToReleaseObj = request.getCountToRelease();
            int countToRelease = (countToReleaseObj != null) ? countToReleaseObj : 0;

            // Check if there are enough sold seats to release
            if (currentSoldSeats < countToRelease) {
                return FsReleaseFareResponseDTO.builder()
                        .success(false)
                        .fareName(fareName)
                        .releasedCount(0)
                        .message("Cannot release " + countToRelease + " seats. Only " + currentSoldSeats
                                + " seats are currently sold.")
                        .build();
            }

            /*
             * Update sold seats count (old logic)
             * This logic assumes that the fare has a field for sold seats.
             * If not, you may need to implement your own logic to track sold seats.
             * fare.setSoldSeats(Math.max(0, currentSoldSeats - countToRelease));
             */

            flightFareRepository.save(fare);

            String message = String.format("Successfully released %d seats for fare '%s'. Reason: %s",
                    countToRelease, fareName, request.getReason() != null ? request.getReason() : "Not specified");

            return FsReleaseFareResponseDTO.builder()
                    .success(true)
                    .fareName(fareName)
                    .releasedCount(countToRelease)
                    .message(message)
                    .build();

        } catch (Exception e) {
            log.error("Error releasing fare for flight {} and fare {}: {}", flightId, fareName, e.getMessage());
            return FsReleaseFareResponseDTO.builder()
                    .success(false)
                    .fareName(fareName)
                    .releasedCount(0)
                    .message("Internal error: " + e.getMessage())
                    .build();
        }
    }

    private void validateFlightCreateRequest(FsFlightCreateRequest request) {
        // Validate flight code uniqueness
        if (flightRepository.existsByCodeAndDeleted(request.code(), false)) {
            throw new BadRequestException("Flight with code '" + request.code() + "' already exists");
        }
        
        // Validate at least one seat class fare is provided
        if (request.seatClassFares() == null || request.seatClassFares().isEmpty()) {
            throw new BadRequestException("At least one seat class fare must be provided");
        }
    }
    
    private void validateFlightUpdateRequest(FsFlightCreateRequest request, UUID flightId) {
        // Check if flight code is unique (excluding current flight)
        Flight existingFlightWithCode = flightRepository.findByCodeAndDeleted(request.code(), false);
        if (existingFlightWithCode != null && !existingFlightWithCode.getId().equals(flightId)) {
            throw new BadRequestException("Flight with code '" + request.code() + "' already exists");
        }
        
        // Validate at least one seat class fare is provided
        if (request.seatClassFares() == null || request.seatClassFares().isEmpty()) {
            throw new BadRequestException("At least one seat class fare must be provided");
        }
    }
    
    private void validateRouteOperational(Route route) {
        // Add route operational status validation if Route entity has status field
        // For now, just validate the route has valid airports
        if (route.getOrigin() == null || route.getDestination() == null) {
            throw new BadRequestException("Route must have valid origin and destination airports");
        }
        
        if (route.getOrigin().getId().equals(route.getDestination().getId())) {
            throw new BadRequestException("Origin and destination airports cannot be the same");
        }
    }
    
    private void validateAircraftAvailability(UUID aircraftId) {
        try {
            // Verify aircraft exists and is active through external service
            FsFlightWithFareDetailsDTO.FsAircraftDTO aircraft = externalAircraftService.getAircraftInfo(aircraftId);
            if (aircraft.id() == null) {
                throw new BadRequestException("Aircraft not found or not active with ID " + aircraftId);
            }
            
            // Verify aircraft has seat configuration
            Map<FareType, List<String>> seatSections = externalAircraftService.getAircraftSeatSections(aircraftId);
            if (seatSections.isEmpty()) {
                throw new BadRequestException("Aircraft has no seat configuration available");
            }
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to validate aircraft availability: {}", e.getMessage());
            throw new BadRequestException("Unable to validate aircraft availability. Aircraft service may be unavailable.");
        }
    }
    
    private void validateFareTypeUniqueness(List<FsFlightCreateRequest.SeatClassFareRequest> seatClassFares) {
        Set<FareType> fareTypes = new HashSet<>();
        for (var fare : seatClassFares) {
            if (!fareTypes.add(fare.fareType())) {
                throw new BadRequestException("Duplicate fare type found: " + fare.fareType().name());
            }
        }
    }
    
    
    private Map<FareType, List<String>> getAircraftSeatSectionsWithErrorHandling(UUID aircraftId) {
        try {
            Map<FareType, List<String>> seatSections = externalAircraftService.getAircraftSeatSections(aircraftId);
            if (seatSections.isEmpty()) {
                throw new BadRequestException("Aircraft has no seat sections configured");
            }
            return seatSections;
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to retrieve seat sections for aircraft {}: {}", aircraftId, e.getMessage());
            throw new BadRequestException("Unable to retrieve aircraft seat configuration. Aircraft service may be unavailable.");
        }
    }
    
    private List<Benefit> validateAndRetrieveBenefits(List<UUID> benefitIds, String fareName) {
        List<Benefit> benefits = new ArrayList<>();
        
        if (benefitIds == null || benefitIds.isEmpty()) {
            return benefits; // No benefits is valid
        }
        
        for (UUID benefitId : benefitIds) {
            try {
                Benefit benefit = benefitRepository.findByIdAndDeleted(benefitId, false)
                        .orElseThrow(() -> new BadRequestException(
                                "Benefit not found with ID " + benefitId + " for fare " + fareName));
                benefits.add(benefit);
            } catch (Exception e) {
                log.error("Error retrieving benefit {} for fare {}: {}", benefitId, fareName, e.getMessage());
                throw new BadRequestException("Failed to retrieve benefit " + benefitId + " for fare " + fareName);
            }
        }
        
        return benefits;
    }
}
