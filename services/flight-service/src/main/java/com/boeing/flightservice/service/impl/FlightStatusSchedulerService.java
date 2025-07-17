package com.boeing.flightservice.service.impl;

import com.boeing.flightservice.entity.Flight;
import com.boeing.flightservice.entity.enums.FlightStatus;
import com.boeing.flightservice.repository.FlightRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class FlightStatusSchedulerService {

    private final FlightRepository flightRepository;

    @Value("${scheduler.flight-status.close-booking-hours-before-departure:2}")
    private int closeBookingHoursBeforeDeparture;

    @Value("${scheduler.flight-status.enabled:true}")
    private boolean schedulerEnabled;

    /**
     * CRON job to set flights to SCHEDULED_CLOSED XX hours before departure
     * Runs every 5 minutes
     */
    @Scheduled(cron = "${scheduler.flight-status.close-booking-cron:0 */5 * * * *}")
    public void closeBookingForFlights() {
        if (!schedulerEnabled) {
            log.debug("Flight status scheduler is disabled");
            return;
        }

        try {
            LocalDateTime cutoffTime = LocalDateTime.now().plusHours(closeBookingHoursBeforeDeparture);
            
            // Find flights that are SCHEDULED_OPEN and have departure time within the cutoff window
            List<Flight> flightsToClose = flightRepository.findByStatusAndDeletedAndDepartureTimeBetween(
                    FlightStatus.SCHEDULED_OPEN,
                    false,
                    LocalDateTime.now(),
                    cutoffTime
            );

            if (flightsToClose.isEmpty()) {
                log.debug("No flights found to close booking for");
                return;
            }

            // Update flight status to SCHEDULED_CLOSED
            for (Flight flight : flightsToClose) {
                flight.setStatus(FlightStatus.SCHEDULED_CLOSE);
                log.info("Closing booking for flight {} departing at {} (within {} hours of departure)", 
                        flight.getCode(), flight.getDepartureTime(), closeBookingHoursBeforeDeparture);
            }

            flightRepository.saveAll(flightsToClose);
            
            log.info("Successfully closed booking for {} flights", flightsToClose.size());
            
        } catch (Exception e) {
            log.error("Error occurred while closing booking for flights: {}", e.getMessage(), e);
        }
    }

    /**
     * CRON job to set flights to COMPLETED after departure time
     * Runs every 10 minutes
     */
    @Scheduled(cron = "${scheduler.flight-status.complete-flights-cron:0 */10 * * * *}")
    public void completeFlights() {
        if (!schedulerEnabled) {
            log.debug("Flight status scheduler is disabled");
            return;
        }

        try {
            LocalDateTime now = LocalDateTime.now();
            
            // Find flights that are not COMPLETED or CANCELLED and have passed their departure time
            List<Flight> flightsToComplete = flightRepository.findByStatusNotInAndDeletedAndDepartureTimeBefore(
                    List.of(FlightStatus.COMPLETED, FlightStatus.CANCELLED),
                    false,
                    now
            );

            if (flightsToComplete.isEmpty()) {
                log.debug("No flights found to mark as completed");
                return;
            }

            // Update flight status to COMPLETED
            for (Flight flight : flightsToComplete) {
                FlightStatus previousStatus = flight.getStatus();
                flight.setStatus(FlightStatus.COMPLETED);
                log.info("Marking flight {} as completed (was: {}, departure time: {})", 
                        flight.getCode(), previousStatus, flight.getDepartureTime());
            }

            flightRepository.saveAll(flightsToComplete);
            
            log.info("Successfully marked {} flights as completed", flightsToComplete.size());
            
        } catch (Exception e) {
            log.error("Error occurred while completing flights: {}", e.getMessage(), e);
        }
    }
}