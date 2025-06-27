package com.boeing.bookingservice.event.listener;

import com.boeing.bookingservice.event.BookingCompletedEvent;
import com.boeing.bookingservice.integration.ls.LoyaltyClient;
import com.boeing.bookingservice.integration.ls.dto.LsEarnPointsRequestDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingCompletedEventListener {

    private final LoyaltyClient loyaltyClient;

    @EventListener
    @Async
    public void handleBookingCompleted(BookingCompletedEvent event) {
        log.info("[BOOKING_COMPLETED_EVENT] Processing booking completion for: {}", event.getBookingReference());
        
        try {
            awardLoyaltyPoints(event);
            
            // 2. Future: Send confirmation email (when notification service is ready)
            // sendBookingConfirmationEmail(event);
            
            // 3. Future: Update user statistics
            // updateUserBookingStatistics(event);
            
            log.info("[BOOKING_COMPLETED_EVENT] Successfully processed booking completion for: {}", 
                     event.getBookingReference());
                     
        } catch (Exception e) {
            log.error("[BOOKING_COMPLETED_EVENT] Error processing booking completion for: {}", 
                      event.getBookingReference(), e);
        }
    }
    
    private void awardLoyaltyPoints(BookingCompletedEvent event) {
        try {
            LsEarnPointsRequestDTO earnRequest = LsEarnPointsRequestDTO.builder()
                    .userId(event.getUserId())
                    .bookingReference(event.getBookingReference())
                    .amountSpent(event.getTotalAmount())
                    .source("BOOKING_COMPLETION")
                    .build();

            loyaltyClient.earnPoints(earnRequest);
            log.info("[LOYALTY_POINTS] Earned points for user {} for booking {} with amount {}",
                    event.getUserId(), event.getBookingReference(), event.getTotalAmount());

        } catch (Exception e) {
            log.error("[LOYALTY_POINTS] Failed to earn loyalty points for booking {}: {}",
                    event.getBookingReference(), e.getMessage(), e);
        }
    }
}
