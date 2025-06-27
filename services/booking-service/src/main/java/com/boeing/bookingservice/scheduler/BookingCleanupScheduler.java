package com.boeing.bookingservice.scheduler;

import com.boeing.bookingservice.model.entity.Booking;
import com.boeing.bookingservice.model.enums.BookingStatus;
import com.boeing.bookingservice.repository.BookingRepository;
import com.boeing.bookingservice.service.BookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingCleanupScheduler {

    private final BookingRepository bookingRepository;
    private final BookingService bookingService;

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void cancelOverduePendingBookingsTask() {
        log.info("Scheduler: Running task to cancel overdue pending bookings at {}", LocalDateTime.now());

        LocalDateTime currentTime = LocalDateTime.now();

        List<Booking> overdueBookings = bookingRepository.findAllByStatusAndPaymentDeadlineBefore(
                BookingStatus.PENDING_PAYMENT,
                currentTime
        );

        if (overdueBookings.isEmpty()) {
            log.info("Scheduler: No overdue pending bookings found.");
            return;
        }

        log.info("Scheduler: Found {} overdue pending bookings to cancel.", overdueBookings.size());

        for (Booking booking : overdueBookings) {
            try {

                bookingService.cancelOverduePendingBooking(booking.getId().toString());

            } catch (Exception e) {
                log.error("Scheduler: Error cancelling overdue booking with reference {}: {}",
                        booking.getBookingReference(), e.getMessage(), e);
            }
        }
        log.info("Scheduler: Finished task to cancel overdue pending bookings.");
    }
}