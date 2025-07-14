package com.boeing.bookingservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${app.rabbitmq.exchanges.events}")
    private String eventsExchangeName;

    @Value("${app.rabbitmq.exchanges.commands}")
    private String commandsExchangeName;

    @Value("${app.rabbitmq.queues.internalFareChecked}")
    private String internalFareCheckedQueueName;

    @Value("${app.rabbitmq.queues.internalBookingCreatedPendingPayment}")
    private String internalBookingCreatedPendingPaymentQueueName;

    @Value("${app.rabbitmq.queues.internalPaymentCompleted}")
    private String internalPaymentCompletedQueueName;

    @Value("${app.rabbitmq.queues.internalCreatePendingBookingCmd}")
    private String internalCreatePendingBookingCmdQueueName;

    @Value("${app.rabbitmq.queues.sagaCreateBookingFailed}")
    private String sagaCreateBookingFailedQueueName;

    @Value("${app.rabbitmq.queues.internalSeatAvailabilityChecked}")
    private String internalSeatAvailabilityCheckedQueueName;
    
    @Value("${app.rabbitmq.queues.internalCreateMultiSegmentPendingBookingCmd}")
    private String internalCreateMultiSegmentPendingBookingCmdQueueName;

    @Value("${app.rabbitmq.routingKeys.internalFareCheckedEvent}")
    private String RK_INTERNAL_FARE_CHECKED_EVENT;

    @Value("${app.rabbitmq.routingKeys.internalBookingCreatedEvent}")
    private String RK_INTERNAL_BOOKING_CREATED_EVENT;

    @Value("${app.rabbitmq.routingKeys.internalPaymentCompletedEvent}")
    private String RK_INTERNAL_PAYMENT_COMPLETED_EVENT;

    @Value("${app.rabbitmq.routingKeys.internalCreatePendingBookingCmdKey}")
    private String RK_INTERNAL_CREATE_PENDING_BOOKING_CMD_KEY;

    @Value("${app.rabbitmq.routingKeys.internalSeatAvailabilityCheckedEvent}")
    private String RK_INTERNAL_SEAT_AVAILABILITY_CHECKED_EVENT;

    @Value("${app.rabbitmq.routingKeys.internalCreateMultiSegmentPendingBookingCmdKey}")
    private String RK_INTERNAL_CREATE_MULTI_SEGMENT_PENDING_BOOKING_CMD_KEY;

    @Value("${app.rabbitmq.routingKeys.sagaCreateBookingFailedEvent}")
    private String RK_SAGA_CREATE_BOOKING_FAILED_EVENT;

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(final ConnectionFactory connectionFactory) {
        final RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter());
        rabbitTemplate.setChannelTransacted(true);
        return rabbitTemplate;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        factory.setChannelTransacted(true);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(eventsExchangeName, true, false);
    }

    @Bean
    public TopicExchange commandsExchange() {
        return new TopicExchange(commandsExchangeName, true, false);
    }

    @Bean
    public Queue internalFareCheckedQueue() {
        return new Queue(internalFareCheckedQueueName, true);
    }

    @Bean
    public Queue internalBookingCreatedPendingPaymentQueue() {
        return new Queue(internalBookingCreatedPendingPaymentQueueName, true);
    }

    @Bean
    public Queue internalPaymentCompletedQueue() {
        return new Queue(internalPaymentCompletedQueueName, true);
    }

    @Bean
    public Queue internalCreatePendingBookingCmdQueue() {
        return new Queue(internalCreatePendingBookingCmdQueueName, true);
    }

    @Bean
    public Queue sagaCreateBookingFailedQueue() {
        return new Queue(sagaCreateBookingFailedQueueName, true);
    }

    @Bean
    public Queue internalSeatAvailabilityCheckedQueue() {
        return new Queue(internalSeatAvailabilityCheckedQueueName, true);
    }
    
    @Bean
    public Queue internalCreateMultiSegmentPendingBookingCmdQueue() {
        return new Queue(internalCreateMultiSegmentPendingBookingCmdQueueName, true);
    }

    // Voucher related queues
    @Bean
    public Queue voucherValidateQueue() {
        return new Queue("voucher.validate.queue", true);
    }

    @Bean
    public Queue voucherValidatedQueue() {
        return new Queue("voucher.validated.queue", true);
    }

    @Bean
    public Queue voucherUseQueue() {
        return new Queue("voucher.use.queue", true);
    }

    @Bean
    public Queue voucherUsedQueue() {
        return new Queue("voucher.used.queue", true);
    }

    @Bean
    public Queue voucherCancelQueue() {
        return new Queue("voucher.cancel.queue", true);
    }

    // Voucher related exchanges
    @Bean
    public TopicExchange voucherValidateExchange() {
        return new TopicExchange("voucher.validate.exchange", true, false);
    }

    @Bean
    public TopicExchange voucherValidatedExchange() {
        return new TopicExchange("voucher.validated.exchange", true, false);
    }

    @Bean
    public TopicExchange voucherUseExchange() {
        return new TopicExchange("voucher.use.exchange", true, false);
    }

    @Bean
    public TopicExchange voucherUsedExchange() {
        return new TopicExchange("voucher.used.exchange", true, false);
    }

    @Bean
    public TopicExchange voucherCancelExchange() {
        return new TopicExchange("voucher.cancel.exchange", true, false);
    }

    @Bean
    public TopicExchange voucherUsageCancelledExchange() {
        return new TopicExchange("voucher.usage.cancelled.exchange", true, false);
    }

    // Voucher related bindings
    @Bean
    public Binding voucherValidateBinding(Queue voucherValidateQueue, TopicExchange voucherValidateExchange) {
        return BindingBuilder.bind(voucherValidateQueue).to(voucherValidateExchange).with("#");
    }

    @Bean
    public Binding voucherValidatedBinding(Queue voucherValidatedQueue, TopicExchange voucherValidatedExchange) {
        return BindingBuilder.bind(voucherValidatedQueue).to(voucherValidatedExchange).with("#");
    }

    @Bean
    public Binding voucherUseBinding(Queue voucherUseQueue, TopicExchange voucherUseExchange) {
        return BindingBuilder.bind(voucherUseQueue).to(voucherUseExchange).with("#");
    }

    @Bean
    public Binding voucherUsedBinding(Queue voucherUsedQueue, TopicExchange voucherUsedExchange) {
        return BindingBuilder.bind(voucherUsedQueue).to(voucherUsedExchange).with("#");
    }

    @Bean
    public Binding voucherCancelBinding(Queue voucherCancelQueue, TopicExchange voucherCancelExchange) {
        return BindingBuilder.bind(voucherCancelQueue).to(voucherCancelExchange).with("#");
    }

    @Bean
    public Binding internalFareCheckedBinding(Queue internalFareCheckedQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(internalFareCheckedQueue).to(eventsExchange).with(RK_INTERNAL_FARE_CHECKED_EVENT);
    }

    @Bean
    public Binding internalBookingCreatedPendingPaymentBinding(Queue internalBookingCreatedPendingPaymentQueue,
            TopicExchange eventsExchange) {
        return BindingBuilder.bind(internalBookingCreatedPendingPaymentQueue).to(eventsExchange)
                .with(RK_INTERNAL_BOOKING_CREATED_EVENT);
    }

    @Bean
    public Binding internalPaymentCompletedBinding(Queue internalPaymentCompletedQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(internalPaymentCompletedQueue).to(eventsExchange)
                .with(RK_INTERNAL_PAYMENT_COMPLETED_EVENT);
    }

    @Bean
    public Binding internalCreatePendingBookingCmdBinding(Queue internalCreatePendingBookingCmdQueue,
            TopicExchange commandsExchange) {
        return BindingBuilder.bind(internalCreatePendingBookingCmdQueue).to(commandsExchange)
                .with(RK_INTERNAL_CREATE_PENDING_BOOKING_CMD_KEY);
    }

    @Bean
    public Binding sagaCreateBookingFailedBinding(Queue sagaCreateBookingFailedQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(sagaCreateBookingFailedQueue).to(eventsExchange)
                .with(RK_SAGA_CREATE_BOOKING_FAILED_EVENT);
    }

    @Bean
    public Binding internalSeatAvailabilityCheckedBinding(Queue internalSeatAvailabilityCheckedQueue,
            TopicExchange eventsExchange) {
        return BindingBuilder.bind(internalSeatAvailabilityCheckedQueue).to(eventsExchange)
                .with(RK_INTERNAL_SEAT_AVAILABILITY_CHECKED_EVENT);
    }
    
    @Bean
    public Binding internalCreateMultiSegmentPendingBookingCmdBinding(Queue internalCreateMultiSegmentPendingBookingCmdQueue,
            TopicExchange commandsExchange) {
        return BindingBuilder.bind(internalCreateMultiSegmentPendingBookingCmdQueue).to(commandsExchange)
                .with(RK_INTERNAL_CREATE_MULTI_SEGMENT_PENDING_BOOKING_CMD_KEY);
    }
}