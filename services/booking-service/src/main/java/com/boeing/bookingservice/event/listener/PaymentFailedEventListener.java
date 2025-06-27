package com.boeing.bookingservice.event.listener;

import com.boeing.bookingservice.event.PaymentFailedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentFailedEventListener {

    @EventListener
    @Async
    public void handlePaymentFailed(PaymentFailedEvent event) {
        log.info("[PAYMENT_FAILED_EVENT] Processing payment failure for booking: {}", event.getBookingReference());
        
        try {
            // 1. Log detailed failure information
            logPaymentFailure(event);
            
            // 2. Future: Send payment failure notification email
            // sendPaymentFailureNotification(event);
            
            // 3. Future: Alert admin system for manual intervention if needed
            // notifyAdminSystemOfPaymentFailure(event);
            
            // 4. Future: Schedule retry logic for certain failure types
            // schedulePaymentRetryIfApplicable(event);
            
            log.info("[PAYMENT_FAILED_EVENT] Successfully processed payment failure for booking: {}", 
                     event.getBookingReference());
                     
        } catch (Exception e) {
            log.error("[PAYMENT_FAILED_EVENT] Error processing payment failure for booking: {}", 
                      event.getBookingReference(), e);
        }
    }
    
    private void logPaymentFailure(PaymentFailedEvent event) {
        log.warn("[PAYMENT_FAILURE_ANALYTICS] Payment failed - " +
                "Booking: {}, User: {}, Method: {}, Error: {}", 
                event.getBookingReference(), 
                event.getUserId(), 
                event.getPaymentMethod(), 
                event.getErrorMessage());
                
        // Future: This could be sent to analytics/monitoring systems
        // analyticsService.trackPaymentFailure(event);
    }
}
