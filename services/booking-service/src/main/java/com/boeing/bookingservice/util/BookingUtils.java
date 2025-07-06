package com.boeing.bookingservice.util;

import com.boeing.bookingservice.dto.request.PassengerInfoDTO;
import com.boeing.bookingservice.dto.request.SeatSelectionDTO;
import com.boeing.bookingservice.model.entity.Booking;
import com.boeing.bookingservice.model.entity.BookingDetail;
import com.boeing.bookingservice.model.entity.Passenger;
import com.boeing.bookingservice.model.enums.BookingDetailStatus;
import com.boeing.bookingservice.repository.PassengerRepository;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Utility class for common booking operations to reduce code duplication
 * between single-segment and multi-segment booking flows.
 */
@Slf4j
public class BookingUtils {

    // Constants for passenger age categories
    public static final double BABY_PRICE = 100000.0; // 100,000 VND for babies under 2 years
    public static final int BABY_AGE_LIMIT = 2; // Babies are under 2 years old
    public static final int CHILD_AGE_LIMIT = 12; // Children are under 12 years old
    
    /**
     * Calculates age category based on date of birth
     * @param dateOfBirth The date of birth of the passenger
     * @return "baby", "child", or "adult" based on age
     */
    public static String getAgeCategory(LocalDate dateOfBirth) {
        if (dateOfBirth == null) return "adult";
        
        LocalDate today = LocalDate.now();
        int age = today.getYear() - dateOfBirth.getYear();
        
        // Adjust for month and day
        if (today.getMonthValue() < dateOfBirth.getMonthValue() || 
            (today.getMonthValue() == dateOfBirth.getMonthValue() && today.getDayOfMonth() < dateOfBirth.getDayOfMonth())) {
            age--;
        }
        
        if (age < BABY_AGE_LIMIT) return "baby";
        if (age < CHILD_AGE_LIMIT) return "child";
        return "adult";
    }
    
    /**
     * Creates a BookingDetail entity for a passenger on a flight segment
     * @param booking The booking entity
     * @param passenger The passenger entity
     * @param flightId The flight ID
     * @param flightCode The flight code
     * @param originAirportCode Origin airport code
     * @param destinationAirportCode Destination airport code
     * @param departureTime Departure time as LocalDate (will be converted to LocalDateTime)
     * @param arrivalTime Arrival time as LocalDate (will be converted to LocalDateTime)
     * @param fareName Selected fare name
     * @param seatCode Selected seat code (null for infants)
     * @param price Price for this passenger on this segment
     * @param bookingCode Booking reference code with optional segment suffix
     * @return A new BookingDetail entity
     */
    public static BookingDetail createBookingDetail(
            Booking booking,
            Passenger passenger,
            UUID flightId,
            String flightCode,
            String originAirportCode,
            String destinationAirportCode,
            LocalDate departureTime,
            LocalDate arrivalTime,
            String fareName,
            String seatCode,
            double price,
            String bookingCode
    ) {
        return BookingDetail.builder()
                .booking(booking)
                .passenger(passenger)
                .flightId(flightId)
                .flightCode(flightCode)
                .originAirportCode(originAirportCode)
                .destinationAirportCode(destinationAirportCode)
                .departureTime(departureTime != null ? departureTime.atStartOfDay() : null)
                .arrivalTime(arrivalTime != null ? arrivalTime.atStartOfDay() : null)
                .selectedFareName(fareName)
                .selectedSeatCode(seatCode)
                .price(price)
                .status(BookingDetailStatus.PENDING_PAYMENT)
                .bookingCode(bookingCode)
                .build();
    }
    
    /**
     * Creates a BookingDetail entity for a passenger on a flight segment
     * Overloaded version that directly accepts LocalDateTime for departure and arrival
     * 
     * @param booking The booking entity
     * @param passenger The passenger entity
     * @param flightId The flight ID
     * @param flightCode The flight code
     * @param originAirportCode Origin airport code
     * @param destinationAirportCode Destination airport code
     * @param departureTime Departure time as LocalDateTime
     * @param arrivalTime Arrival time as LocalDateTime
     * @param fareName Selected fare name
     * @param seatCode Selected seat code (null for infants)
     * @param price Price for this passenger on this segment
     * @param bookingCode Booking reference code with optional segment suffix
     * @return A new BookingDetail entity
     */
    public static BookingDetail createBookingDetail(
            Booking booking,
            Passenger passenger,
            UUID flightId,
            String flightCode,
            String originAirportCode,
            String destinationAirportCode,
            LocalDateTime departureTime,
            LocalDateTime arrivalTime,
            String fareName,
            String seatCode,
            double price,
            String bookingCode
    ) {
        return BookingDetail.builder()
                .booking(booking)
                .passenger(passenger)
                .flightId(flightId)
                .flightCode(flightCode)
                .originAirportCode(originAirportCode)
                .destinationAirportCode(destinationAirportCode)
                .departureTime(departureTime)
                .arrivalTime(arrivalTime)
                .selectedFareName(fareName)
                .selectedSeatCode(seatCode)
                .price(price)
                .status(BookingDetailStatus.PENDING_PAYMENT)
                .bookingCode(bookingCode)
                .build();
    }
    
    /**
     * Calculate price for a passenger based on age category and fare
     * @param ageCategory The age category (baby, child, adult)
     * @param baseFarePrice The base fare price for this segment
     * @param selectedFareName The selected fare name/class
     * @return The calculated price for this passenger
     */
    public static double calculatePassengerPrice(String ageCategory, double baseFarePrice, String selectedFareName) {
        // Babies have fixed pricing regardless of fare class and don't need seats
        if ("baby".equals(ageCategory)) {
            return BABY_PRICE;
        }
        
        // For adults and children, apply fare multipliers
        double fareMultiplier = 1.0;
        if (selectedFareName != null) {
            switch (selectedFareName.toUpperCase()) {
                case "ECONOMY":
                    fareMultiplier = 1.0;
                    break;
                case "PREMIUM_ECONOMY":
                    fareMultiplier = 1.5;
                    break;
                case "BUSINESS":
                    fareMultiplier = 2.5;
                    break;
                case "FIRST_CLASS":
                    fareMultiplier = 4.0;
                    break;
                default:
                    fareMultiplier = 1.0;
            }
        }
        
        double calculatedPrice = baseFarePrice * fareMultiplier;
        
        // Apply child discount if applicable
        if ("child".equals(ageCategory)) {
            // Children typically get 25% discount
            calculatedPrice = calculatedPrice * 0.75;
        }
        
        return Math.round(calculatedPrice * 100.0) / 100.0; // Round to 2 decimal places
    }
    
    /**
     * Finds or creates a passenger entity
     * @param passengerId The passenger ID (if existing)
     * @param userId The user ID
     * @param passengerRequest The passenger details from the request
     * @param passengerRepository The passenger repository
     * @return The found or created passenger entity
     */
    public static Passenger findOrCreatePassenger(
            UUID passengerId, 
            UUID userId, 
            PassengerInfoDTO passengerRequest,
            Passenger newPassengerEntity,
            PassengerRepository passengerRepository
    ) {
        if (passengerId != null) {
            return passengerRepository.findByIdAndUserId(passengerId, userId)
                    .orElseGet(() -> {
                        log.warn("Passenger ID {} (provided by user {}) not found or not owned. Creating new passenger.", 
                                passengerId, userId);
                        newPassengerEntity.setId(null);
                        newPassengerEntity.setUserId(userId);
                        return passengerRepository.save(newPassengerEntity);
                    });
        } else {
            newPassengerEntity.setUserId(userId);
            return passengerRepository.save(newPassengerEntity);
        }
    }
    
    /**
     * Creates booking details for all passengers on a flight segment
     * 
     * @param booking The booking entity
     * @param passengerEntities Map of passenger index to passenger entity
     * @param passengerInfos List of passenger info DTOs in the same order as the map keys
     * @param flightId Flight ID for this segment
     * @param flightCode Flight code
     * @param originAirportCode Origin airport code
     * @param destinationAirportCode Destination airport code
     * @param departureTime Departure time
     * @param arrivalTime Arrival time
     * @param selectedFareName Selected fare name
     * @param seatSelections Map of passenger index to seat code (can be null)
     * @param baseFarePrice Base fare price per passenger
     * @param bookingCode Booking reference code with optional segment suffix
     * @return List of created booking details and the total actual amount
     */
    public static BookingDetailsResult createBookingDetailsForSegment(
            Booking booking,
            Map<Integer, Passenger> passengerEntities,
            List<PassengerInfoDTO> passengerInfos,
            UUID flightId,
            String flightCode,
            String originAirportCode,
            String destinationAirportCode,
            LocalDateTime departureTime,
            LocalDateTime arrivalTime,
            String selectedFareName,
            Map<Integer, String> seatSelections,
            double baseFarePrice,
            String bookingCode
    ) {
        List<BookingDetail> details = new ArrayList<>();
        double actualTotalAmount = 0.0;
        
        for (int passengerIndex = 0; passengerIndex < passengerInfos.size(); passengerIndex++) {
            PassengerInfoDTO passengerInfo = passengerInfos.get(passengerIndex);
            Passenger passengerEntity = passengerEntities.get(passengerIndex);
            
            if (passengerEntity == null || passengerInfo == null) {
                log.warn("Missing passenger entity or info at index {}, skipping", passengerIndex);
                continue;
            }
            
            // Get seat code for this passenger if any
            String seatCode = seatSelections != null ? seatSelections.get(passengerIndex) : null;
            
            // Don't assign seats to infants
            String ageCategory = getAgeCategory(passengerInfo.getDateOfBirth());
            if ("baby".equals(ageCategory) && seatCode != null) {
                log.warn("Baby passenger {} has seat assignment {}, removing seat assignment", 
                        passengerInfo.getFirstName(), seatCode);
                seatCode = null;
            }
            
            // Calculate price for this passenger
            double detailPrice = calculatePassengerPrice(ageCategory, baseFarePrice, selectedFareName);
            detailPrice = Math.round(detailPrice * 100.0) / 100.0;
            actualTotalAmount += detailPrice;
            
            // Create booking detail
            BookingDetail detail = createBookingDetail(
                    booking,
                    passengerEntity,
                    flightId,
                    flightCode,
                    originAirportCode,
                    destinationAirportCode,
                    departureTime,
                    arrivalTime,
                    selectedFareName,
                    seatCode,
                    detailPrice,
                    bookingCode
            );
            
            details.add(detail);
        }
        
        return new BookingDetailsResult(details, Math.round(actualTotalAmount * 100.0) / 100.0);
    }
    
    /**
     * Simple result class to hold booking details and their total amount
     */
    public static class BookingDetailsResult {
        private final List<BookingDetail> details;
        private final double totalAmount;
        
        public BookingDetailsResult(List<BookingDetail> details, double totalAmount) {
            this.details = details;
            this.totalAmount = totalAmount;
        }
        
        public List<BookingDetail> getDetails() {
            return details;
        }
        
        public double getTotalAmount() {
            return totalAmount;
        }
    }
    
    /**
     * Converts a list of seat selections with passenger indices to a map for easier lookup
     * 
     * @param seatSelections List of seat selections
     * @return Map of passenger index to seat code
     */
    public static Map<Integer, String> createSeatSelectionMap(List<SeatSelectionDTO> seatSelections) {
        Map<Integer, String> seatMap = new HashMap<>();
        
        if (seatSelections == null || seatSelections.isEmpty()) {
            return seatMap;
        }
        
        for (SeatSelectionDTO seatSelection : seatSelections) {
            if (seatSelection != null && seatSelection.getPassengerIndex() != null && seatSelection.getSeatCode() != null) {
                seatMap.put(seatSelection.getPassengerIndex(), seatSelection.getSeatCode());
            }
        }
        
        return seatMap;
    }
}
