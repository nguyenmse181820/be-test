package com.boeing.bookingservice.saga.handler;

import com.boeing.bookingservice.integration.fs.FlightClient;
import com.boeing.bookingservice.integration.fs.dto.FsReleaseFareRequestDTO;
import com.boeing.bookingservice.integration.fs.dto.FsReleaseFareResponseDTO;
import com.boeing.bookingservice.model.entity.Booking;
import com.boeing.bookingservice.repository.BookingRepository;
import com.boeing.bookingservice.saga.event.CreateBookingSagaFailedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingCompensationHandler {

    private final FlightClient flightClient;
    private final BookingRepository bookingRepository;

    @RabbitListener(queues = "#{@environment.getProperty('app.rabbitmq.queues.sagaCreateBookingFailed')}")
    public void handleBookingFailed(CreateBookingSagaFailedEvent event) {
        log.info("Received booking failed event for saga: {}, booking: {}",
                event.sagaId(), event.bookingReference());

        try {
            compensateBookingFailure(event);

            log.info("Successfully completed compensation for failed booking saga: {}", event.sagaId());

        } catch (Exception e) {
            log.error("Error during compensation for failed booking saga: {}", event.sagaId(), e);
        }
    }

    private void compensateBookingFailure(CreateBookingSagaFailedEvent event) {
        String bookingReference = event.bookingReference();

        // Find the booking to get flight details
        Optional<Booking> bookingOpt = bookingRepository.findByBookingReference(bookingReference);
        if (bookingOpt.isEmpty()) {
            log.warn("Booking not found for reference: {} during compensation", bookingReference);
            return;
        }

        Booking booking = bookingOpt.get();
        if (event.flightId() != null && event.fareName() != null) {
            try {
                FsReleaseFareRequestDTO request = FsReleaseFareRequestDTO.builder()
                        .bookingReference(bookingReference)
                        .fareName(event.fareName())
                        .countToRelease(booking.getPassengerCount())
                        .reason("BOOKING_FAILED: " + event.failureReason())
                        .build();

                FsReleaseFareResponseDTO response = flightClient.releaseFare(event.flightId(), event.fareName(), request);

                if (response != null && response.isSuccess()) {
                    log.info("Successfully released fare hold for failed booking: {}", bookingReference);
                } else {
                    log.warn("Failed to release fare hold for failed booking: {}", bookingReference);
                }
            } catch (Exception e) {
                log.error("Error releasing fare hold for failed booking: {}", bookingReference, e);
            }
        }

        // TODO: Add other compensation actions as needed:
        // - Revert loyalty points if they were deducted
        // - Cancel any payment authorization
        // - Send cancellation notification to customer

        log.info("Compensation completed for booking: {}, reason: {}",
                bookingReference, event.failureReason());
    }
}
