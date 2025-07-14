package com.boeing.bookingservice.saga.handler;

import com.boeing.bookingservice.dto.response.ApiResponse;
import com.boeing.bookingservice.integration.ls.LoyaltyClient;
import com.boeing.bookingservice.integration.ls.dto.LsUseVoucherRequestDTO;
import com.boeing.bookingservice.integration.ls.dto.LsUseVoucherResponseDTO;
import com.boeing.bookingservice.integration.ls.dto.LsValidateVoucherRequestDTO;
import com.boeing.bookingservice.integration.ls.dto.LsValidateVoucherResponseDTO;
import com.boeing.bookingservice.saga.command.CancelVoucherUsageCommand;
import com.boeing.bookingservice.saga.command.UseVoucherCommand;
import com.boeing.bookingservice.saga.command.ValidateVoucherCommand;
import com.boeing.bookingservice.saga.event.VoucherUsedEvent;
import com.boeing.bookingservice.saga.event.VoucherValidatedEvent;
import com.boeing.bookingservice.saga.event.VoucherUsageCancelledEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class VoucherSagaHandler {
    
    private final LoyaltyClient loyaltyClient;
    private final RabbitTemplate rabbitTemplate;
    
    @RabbitListener(queues = "voucher.validate.queue")
    public void handleValidateVoucher(ValidateVoucherCommand command) {
        log.info("Handling voucher validation for saga: {}, voucher: {}", 
                command.sagaId(), command.voucherCode());
        
        try {
            LsValidateVoucherRequestDTO request = LsValidateVoucherRequestDTO.builder()
                    .voucherCode(command.voucherCode())
                    .userId(command.userId())
                    .bookingAmount(command.bookingAmountBeforeDiscount())
                    .build();
            
            ApiResponse<LsValidateVoucherResponseDTO> apiResponse = loyaltyClient.validateVoucher(request);
            LsValidateVoucherResponseDTO response = apiResponse.getData();
            
            VoucherValidatedEvent event = new VoucherValidatedEvent(
                    command.sagaId(),
                    response.isValid(),
                    command.voucherCode(),
                    response.getDiscountAmount(),
                    response.getErrorMessage(),
                    command
            );
            
            rabbitTemplate.convertAndSend("voucher.validated.exchange", "", event);
            log.info("Voucher validation completed for saga: {}, valid: {}, discount: {}", 
                    command.sagaId(), response.isValid(), response.getDiscountAmount());
            
        } catch (Exception e) {
            log.error("Error validating voucher for saga: {}", command.sagaId(), e);
            
            VoucherValidatedEvent event = new VoucherValidatedEvent(
                    command.sagaId(),
                    false,
                    command.voucherCode(),
                    0.0,
                    "Voucher validation failed: " + e.getMessage(),
                    command
            );
            
            rabbitTemplate.convertAndSend("voucher.validated.exchange", "", event);
        }
    }
    
    @RabbitListener(queues = "voucher.use.queue")
    public void handleUseVoucher(UseVoucherCommand command) {
        log.info("Handling voucher usage for saga: {}, voucher: {}", 
                command.sagaId(), command.voucherCode());
        
        try {
            ApiResponse<LsUseVoucherResponseDTO> apiResponse = loyaltyClient.useVoucher(command.voucherCode());
            LsUseVoucherResponseDTO response = apiResponse.getData();
            
            VoucherUsedEvent event = new VoucherUsedEvent(
                    command.sagaId(),
                    response.isSuccess(),
                    response.getErrorMessage(),
                    command
            );
            
            rabbitTemplate.convertAndSend("voucher.used.exchange", "", event);
            log.info("Voucher usage completed for saga: {}, success: {}", 
                    command.sagaId(), response.isSuccess());
            
        } catch (Exception e) {
            log.error("Error using voucher for saga: {}", command.sagaId(), e);
            
            VoucherUsedEvent event = new VoucherUsedEvent(
                    command.sagaId(),
                    false,
                    "Voucher usage failed: " + e.getMessage(),
                    command
            );
            
            rabbitTemplate.convertAndSend("voucher.used.exchange", "", event);
        }
    }
    
    @RabbitListener(queues = "voucher.cancel.queue")
    public void handleCancelVoucherUsage(CancelVoucherUsageCommand command) {
        log.info("Handling voucher usage cancellation for saga: {}, voucher: {}, reason: {}", 
                command.sagaId(), command.voucherCode(), command.reason());
        
        try {
            // Call loyalty service to restore voucher to unused state
            loyaltyClient.cancelVoucherUsage(command.voucherCode(), command.userId());
            
            VoucherUsageCancelledEvent event = new VoucherUsageCancelledEvent(
                    command.sagaId(),
                    command.voucherCode(),
                    command.userId(),
                    true,
                    null
            );
            
            rabbitTemplate.convertAndSend("voucher.usage.cancelled.exchange", "", event);
            log.info("Voucher usage cancellation completed for saga: {}, reason: {}", 
                    command.sagaId(), command.reason());
            
        } catch (Exception e) {
            log.error("Error cancelling voucher usage for saga: {}, reason: {}", 
                    command.sagaId(), command.reason(), e);
            
            VoucherUsageCancelledEvent event = new VoucherUsageCancelledEvent(
                    command.sagaId(),
                    command.voucherCode(),
                    command.userId(),
                    false,
                    "Voucher cancellation failed: " + e.getMessage()
            );
            
            rabbitTemplate.convertAndSend("voucher.usage.cancelled.exchange", "", event);
        }
    }
}