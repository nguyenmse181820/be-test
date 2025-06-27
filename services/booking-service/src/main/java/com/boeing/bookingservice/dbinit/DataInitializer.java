package com.boeing.bookingservice.dbinit;

import com.boeing.bookingservice.model.entity.*;
import com.boeing.bookingservice.model.enums.*;
import com.boeing.bookingservice.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final BookingRepository bookingRepository;
    private final PassengerRepository passengerRepository;
    private final PaymentRepository paymentRepository;

    @Override
    @Transactional // Add transaction here
    public void run(String... args) {
        if (bookingRepository.count() == 0) {
            createTestData();
        } else {
            bookingRepository.findAll().forEach(booking ->
                    log.info("   📦 Booking: {} | Amount: {} VND | Status: {}",
                            booking.getBookingReference(),
                            booking.getTotalAmount(),
                            booking.getStatus())
            );
        }
    }

    private void createTestData() {
        // Create and save passengers first
        Passenger passenger1 = createAndSavePassenger("John", "Doe", "VN", "123456789");
        Passenger passenger2 = createAndSavePassenger("Jane", "Smith", "US", "987654321");
        Passenger passenger3 = createAndSavePassenger("Nguyen", "Van A", "VN", "111222333");
        Passenger passenger4 = createAndSavePassenger("Michael", "Johnson", "UK", "444555666");
        Passenger passenger5 = createAndSavePassenger("Tran", "Thi B", "VN", "777888999");
        Passenger passenger6 = createAndSavePassenger("David", "Wilson", "AU", "101010101");
        Passenger passenger7 = createAndSavePassenger("Le", "Van C", "VN", "202020202");
        Passenger passenger8 = createAndSavePassenger("Sarah", "Brown", "CA", "303030303");
        Passenger passenger9 = createAndSavePassenger("Pham", "Van D", "VN", "404040404");
        Passenger passenger10 = createAndSavePassenger("Robert", "Davis", "FR", "505050505");

        // Create bookings with the saved passengers
        createBooking("BKG-TEST001", passenger1, 2500000.0, "ECONOMY");
        createBooking("BKG-TEST002", passenger2, 1800000.0, "ECONOMY");
        createBooking("BKG-PENDING01", passenger3, 3200000.0, "ECONOMY");
        createBooking("BKG-TEST003", passenger4, 4100000.0, "ECONOMY");
        createBooking("BKG-TEST004", passenger5, 2750000.0, "FIRST_CLASS");
        createBooking("BKG-PENDING02", passenger6, 3850000.0, "FIRST_CLASS");
        createBooking("BKG-TEST005", passenger7, 2200000.0, "FIRST_CLASS");
        createBooking("BKG-TEST006", passenger8, 3300000.0, "BUSINESS");
        createBooking("BKG-PENDING03", passenger9, 2900000.0, "BUSINESS");
        createBooking("BKG-TEST007", passenger10, 4500000.0, "BUSINESS");
    }

    private Passenger createAndSavePassenger(String firstName, String lastName, String nationality, String idNumber) {
        Passenger passenger = Passenger.builder()
                .firstName(firstName)
                .lastName(lastName)
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .gender(PassengerGender.MALE)
                .nationality(nationality)
                .title(PassengerTitle.MR)
                .idNumber(idNumber)
                .passportNumber("PP" + idNumber)
                .countryOfIssue(nationality)
                .passportExpiryDate(LocalDate.of(2030, 1, 1))
                .userId(UUID.fromString("12345678-1234-1234-1234-123456789012"))
                .build();

        return passengerRepository.save(passenger); // Save and return managed entity
    }

    private void createBooking(String bookingRef, Passenger passenger, Double amount, String selectedFareName) {
        // Create booking
        Booking booking = Booking.builder()
                .bookingReference(bookingRef)
                .userId(passenger.getUserId())
                .bookingDate(LocalDate.now())
                .totalAmount(amount)
                .status(BookingStatus.PENDING_PAYMENT)
                .type(BookingType.STANDARD)
                .paymentDeadline(LocalDateTime.now().plusHours(2))
                .bookingDetails(new ArrayList<>())
                .payments(new ArrayList<>())
                .build();

        // Create booking detail - use the managed passenger entity directly
        BookingDetail detail = BookingDetail.builder()
                .booking(booking)
                .passenger(passenger) // Use the managed passenger entity
                .flightId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                .flightCode("VJ" + bookingRef.substring(bookingRef.length() - 3))
                .originAirportCode("SGN")
                .destinationAirportCode("HAN")
                .departureTime(LocalDateTime.now().plusDays(7))
                .arrivalTime(LocalDateTime.now().plusDays(7).plusHours(2))
                .selectedFareName(selectedFareName)
                .price(amount)
                .status(BookingDetailStatus.PENDING_PAYMENT)
                .bookingCode(bookingRef)
                .build();

        booking.getBookingDetails().add(detail);

        // Create payment
        Payment payment = Payment.builder()
                .booking(booking)
                .bookingReference(bookingRef)
                .vnpTxnRef(bookingRef)
                .orderCode(System.currentTimeMillis() + (long)(Math.random() * 1000))
                .amount(amount)
                .currency("VND")
                .status(PaymentStatus.PENDING)
                .paymentType(PaymentType.BOOKING)
                .paymentMethod(PaymentMethod.VNPAY_QR)
                .paymentDate(LocalDateTime.now())
                .description("Payment for booking " + bookingRef)
                .build();

        booking.getPayments().add(payment);

        // Save booking (this will cascade save the payment and booking detail)
        bookingRepository.save(booking);
    }
}