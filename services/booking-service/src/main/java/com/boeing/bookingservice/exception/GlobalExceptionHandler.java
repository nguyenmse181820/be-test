package com.boeing.bookingservice.exception;

import com.boeing.bookingservice.dto.response.ApiResponse;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleResourceNotFoundException(ResourceNotFoundException ex, WebRequest request) {
        log.error("ResourceNotFoundException: {} on request: {}", ex.getMessage(), request.getDescription(false));
        ApiResponse<Object> errorResponse = ApiResponse.builder()
                .success(false)
                .message(ex.getMessage())
                .errorCode(HttpStatus.NOT_FOUND.value())
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<Object>> handleBadRequestException(BadRequestException ex, WebRequest request) {
        log.error("BadRequestException: {} on request: {}", ex.getMessage(), request.getDescription(false));
        ApiResponse<Object> errorResponse = ApiResponse.builder()
                .success(false)
                .message(ex.getMessage())
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(BookingValidationException.class)
    public ResponseEntity<ApiResponse<Object>> handleBookingValidationException(BookingValidationException ex, WebRequest request) {
        log.error("BookingValidationException: {} on request: {}", ex.getMessage(), request.getDescription(false));
        ApiResponse<Object> errorResponse = ApiResponse.builder()
                .success(false)
                .message(ex.getMessage())
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(SeatUnavailableException.class)
    public ResponseEntity<ApiResponse<Object>> handleSeatUnavailableException(SeatUnavailableException ex, WebRequest request) {
        log.error("SeatUnavailableException: {} on request: {}", ex.getMessage(), request.getDescription(false));
        ApiResponse<Object> errorResponse = ApiResponse.builder()
                .success(false)
                .message(ex.getMessage())
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(SagaProcessingException.class)
    public ResponseEntity<ApiResponse<Object>> handleSagaProcessingException(SagaProcessingException ex, WebRequest request) {
        log.error("SagaProcessingException: {} on request: {}", ex.getMessage(), request.getDescription(false), ex);
        ApiResponse<Object> errorResponse = ApiResponse.builder()
                .success(false)
                .message("An error occurred while processing your request. Please try again later.")
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidationExceptions(MethodArgumentNotValidException ex, WebRequest request) {
        log.warn("MethodArgumentNotValidException: {} on request: {}", ex.getMessage(), request.getDescription(false));
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        ApiResponse<Object> errorResponse = ApiResponse.builder()
                .success(false)
                .message("Validation failed")
                .data(errors)
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccessDeniedException(AccessDeniedException ex, WebRequest request) {
        log.warn("AccessDeniedException: {} on request: {}", ex.getMessage(), request.getDescription(false));
        ApiResponse<Object> errorResponse = ApiResponse.builder()
                .success(false)
                .message("Access Denied: You do not have permission to access this resource.")
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGlobalException(Exception ex, WebRequest request) {
        log.error("Unhandled Exception: {} on request: {}", ex.getMessage(), request.getDescription(false), ex);
        ApiResponse<Object> errorResponse = ApiResponse.builder()
                .success(false)
                .message("An unexpected internal server error occurred.")
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    @ExceptionHandler(VNPayException.class)
    public ResponseEntity<ApiResponse<Object>> handleVNPayException(VNPayException ex, WebRequest request) {
        log.error("VNPay error: {} - Response Code: {}", ex.getMessage(), ex.getResponseCode());

        String userMessage = getUserFriendlyVNPayMessage(ex.getResponseCode());

        ApiResponse<Object> errorResponse = ApiResponse.builder()
                .success(false)
                .message(userMessage)
                .errorCode(HttpStatus.PAYMENT_REQUIRED.value())
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.PAYMENT_REQUIRED);
    }

    @ExceptionHandler(PaymentProcessingException.class)
    public ResponseEntity<ApiResponse<Object>> handlePaymentProcessingException(PaymentProcessingException ex, WebRequest request) {
        log.error("Payment processing exception: {}", ex.getMessage());

        ApiResponse<Object> errorResponse = ApiResponse.builder()
                .success(false)
                .message(ex.getMessage())
                .errorCode(HttpStatus.BAD_REQUEST.value())
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ApiResponse<Object>> handleCircuitBreakerException(CallNotPermittedException ex, WebRequest request) {
        log.error("Circuit breaker is open: {} on request: {}", ex.getMessage(), request.getDescription(false));
        ApiResponse<Object> errorResponse = ApiResponse.builder()
                .success(false)
                .message("Service is temporarily unavailable. Please try again later.")
                .errorCode(HttpStatus.SERVICE_UNAVAILABLE.value())
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.SERVICE_UNAVAILABLE);
    }

    /**
     * Converts VNPay response codes to user-friendly messages
     */
    private String getUserFriendlyVNPayMessage(String responseCode) {
        if (responseCode == null) {
            return "Payment processing failed. Please try again.";
        }

        return switch (responseCode) {
            case "00" -> "Payment successful";
            case "07" -> "Transaction was deducted successfully. Transaction is suspected of fraud (related to gray card/black card)";
            case "09" -> "Customer's card/account has not registered for Internet banking service at the bank";
            case "10" -> "Customer has entered wrong card/account information more than 3 times";
            case "11" -> "Payment deadline has expired. Please try again";
            case "12" -> "Customer's card/account is locked";
            case "13" -> "Customer entered wrong OTP. Please try again";
            case "24" -> "Customer canceled transaction";
            case "51" -> "Customer's account has insufficient balance";
            case "65" -> "Customer's account has exceeded daily transaction limit";
            case "75" -> "Payment bank is under maintenance";
            case "79" -> "Customer entered payment password incorrectly more than allowed";
            case "99" -> "Other errors (may be due to bank system maintenance)";
            default -> "Payment failed. Please try again or contact support. Error code: " + responseCode;
        };
    }
}