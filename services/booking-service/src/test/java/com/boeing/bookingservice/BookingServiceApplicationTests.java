package com.boeing.bookingservice;

import com.boeing.bookingservice.config.TestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@Import(TestConfig.class)
@TestPropertySource(properties = {
    "spring.rabbitmq.listener.simple.auto-startup=false",
    "spring.cloud.config.enabled=false",
    "eureka.client.enabled=false",
    "jwt.secret.key=dGVzdFNlY3JldEtleUZvclRlc3RpbmdQdXJwb3Nlc09ubHlEb05vdFVzZUluUHJvZHVjdGlvbg==",
    "app.rabbitmq.exchanges.events=test-events-exchange",
    "app.rabbitmq.exchanges.commands=test-commands-exchange",
    "app.rabbitmq.queues.internalSeatsChecked=test-internal-seats-checked-queue",
    "app.rabbitmq.queues.internalVoucherValidated=test-internal-voucher-validated-queue",
    "app.rabbitmq.queues.internalBookingCreatedPendingPayment=test-internal-booking-created-pending-payment-queue",
    "app.rabbitmq.queues.internalPaymentCompleted=test-internal-payment-completed-queue",
    "app.rabbitmq.queues.internalCreatePendingBookingCmd=test-internal-create-pending-booking-cmd-queue"
})
class BookingServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
