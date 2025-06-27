package com.boeing.bookingservice.controller;

import com.boeing.bookingservice.dto.request.CreateBookingRequestDTO;
import com.boeing.bookingservice.dto.request.PassengerInfoDTO;
import com.boeing.bookingservice.dto.request.SeatSelectionDTO;
import com.boeing.bookingservice.dto.response.BookingInitiatedResponseDTO;
import com.boeing.bookingservice.model.enums.PassengerGender;
import com.boeing.bookingservice.model.enums.PassengerTitle;
import com.boeing.bookingservice.model.enums.PaymentStatus;
import com.boeing.bookingservice.saga.orchestrator.CreateBookingSagaOrchestrator;
import com.boeing.bookingservice.service.BookingService;
import com.boeing.bookingservice.service.impl.BookingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class BookingControllerIntegrationTest {

    @Mock
    private CreateBookingSagaOrchestrator createBookingSagaOrchestrator;

    @InjectMocks
    private BookingServiceImpl bookingService;

    @Captor
    private ArgumentCaptor<CreateBookingRequestDTO> createBookingRequestCaptor;

    @Captor
    private ArgumentCaptor<UUID> userIdCaptor;

    @Captor
    private ArgumentCaptor<String> bookingReferenceCaptor;

    private UUID userId;
    private UUID flightId;
    private CreateBookingRequestDTO createBookingRequest;
    private LocalDateTime paymentDeadline;
    private String stripePaymentUrl;
    private String stripeClientSecret;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        flightId = UUID.randomUUID();
        paymentDeadline = LocalDateTime.now().plusMinutes(15);
        stripePaymentUrl = "https://stripe.com/pay/mock_integration_session_id";
        stripeClientSecret = "mock_integration_client_secret";

        // Setup test data
        createBookingRequest = new CreateBookingRequestDTO();
        createBookingRequest.setFlightId(flightId);
        createBookingRequest.setPaymentMethod("STRIPE_CARD");

        List<SeatSelectionDTO> seatSelections = new ArrayList<>();
        SeatSelectionDTO seatSelection = new SeatSelectionDTO();
        seatSelection.setSeatCode("A1");

        PassengerInfoDTO passengerInfo = PassengerInfoDTO.builder()
                .firstName("John")
                .lastName("Doe")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .gender(PassengerGender.MALE)
                .nationality("US")
                .title(PassengerTitle.MR)
                .idNumber("123456789")
                .passportNumber("AB123456")
                .countryOfIssue("US")
                .passportExpiryDate(LocalDate.now().plusYears(5))
                .build();
        seatSelection.setPassengerInfo(passengerInfo);

        seatSelections.add(seatSelection);
        createBookingRequest.setSeatSelections(seatSelections);
    }

    @Test
    void initiateBookingCreationSaga_ShouldCallOrchestratorAndReturnResponse() throws Exception {
        // Arrange
        doAnswer(invocation -> {
            String bookingRef = invocation.getArgument(2);
            // Simulate the orchestrator completing the saga with a payment URL
            bookingService.completeSagaInitiationWithPaymentUrl(
                    bookingRef,
                    BigDecimal.valueOf(100),
                    stripePaymentUrl,
                    stripeClientSecret,
                    paymentDeadline
            );
            return null;
        }).when(createBookingSagaOrchestrator).startSaga(any(), any(), any());

        // Act
        BookingInitiatedResponseDTO result = bookingService.initiateBookingCreationSaga(createBookingRequest, userId);

        // Assert
        verify(createBookingSagaOrchestrator).startSaga(
                createBookingRequestCaptor.capture(),
                userIdCaptor.capture(),
                bookingReferenceCaptor.capture()
        );

        assertEquals(createBookingRequest, createBookingRequestCaptor.getValue());
        assertEquals(userId, userIdCaptor.getValue());
        assertNotNull(bookingReferenceCaptor.getValue());

        assertNotNull(result);
        assertEquals(PaymentStatus.PENDING, result.getPaymentStatus());
        assertEquals(stripePaymentUrl, result.getStripePaymentUrl());
        assertEquals(stripeClientSecret, result.getStripeClientSecret());
        assertEquals(BigDecimal.valueOf(100), result.getTotalAmount());
        assertEquals(paymentDeadline, result.getPaymentDeadline());
    }
}