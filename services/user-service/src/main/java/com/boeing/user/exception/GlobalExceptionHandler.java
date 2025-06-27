package com.boeing.user.exception;

import com.boeing.user.dto.response.ApiResponse;
import com.twilio.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Object>> handleBadCredentialsException(BadCredentialsException ex, WebRequest request) {
        logger.warn("Bad credentials attempt: {}", ex.getMessage());
        ApiResponse<Object> apiResponse = ApiResponse.builder()
                .success(false)
                .message("Invalid email or password.")
                .errorCode(HttpStatus.UNAUTHORIZED.value())
                .build();
        return new ResponseEntity<>(apiResponse, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleUsernameNotFoundException(UsernameNotFoundException ex, WebRequest request) {
        logger.warn("Username not found: {}", ex.getMessage());
        ApiResponse<Object> apiResponse = ApiResponse.builder()
                .success(false)
                .message("Invalid email or password.")
                .errorCode(HttpStatus.UNAUTHORIZED.value())
                .build();
        return new ResponseEntity<>(apiResponse, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccessDeniedException(AccessDeniedException ex, WebRequest request) {
        logger.warn("Access denied: {}", ex.getMessage());
        ApiResponse<Object> apiResponse = ApiResponse.builder()
                .success(false)
                .message("You do not have permission to access this resource.")
                .errorCode(HttpStatus.FORBIDDEN.value())
                .build();
        return new ResponseEntity<>(apiResponse, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(BusinessLogicException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusinessLogicException(BusinessLogicException ex, WebRequest request) {
        logger.error("Business logic error: {}", ex.getMessage());
        ApiResponse<Object> apiResponse = ApiResponse.builder()
                .success(false)
                .message(ex.getMessage())
                .errorCode(HttpStatus.BAD_REQUEST.value())
                .build();
        return new ResponseEntity<>(apiResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleResourceNotFoundException(ResourceNotFoundException ex, WebRequest request) {
        logger.warn("Resource not found: {}", ex.getMessage());
        ApiResponse<Object> apiResponse = ApiResponse.builder()
                .success(false)
                .message(ex.getMessage())
                .errorCode(HttpStatus.NOT_FOUND.value())
                .build();
        return new ResponseEntity<>(apiResponse, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiResponse<Object>> handleDuplicateResourceException(DuplicateResourceException ex, WebRequest request) {
        logger.warn("Duplicate resource: {}", ex.getMessage());
        ApiResponse<Object> apiResponse = ApiResponse.builder()
                .success(false)
                .message(ex.getMessage())
                .errorCode(HttpStatus.CONFLICT.value())
                .build();
        return new ResponseEntity<>(apiResponse, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(InvalidOtpException.class)
    public ResponseEntity<ApiResponse<Object>> handleInvalidOtpException(InvalidOtpException ex, WebRequest request) {
        logger.warn("Invalid OTP: {}", ex.getMessage());
        ApiResponse<Object> apiResponse = ApiResponse.builder()
                .success(false)
                .message(ex.getMessage())
                .errorCode(HttpStatus.BAD_REQUEST.value())
                .build();
        return new ResponseEntity<>(apiResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Object>> handleTwilioApiException(ApiException ex, WebRequest request) {
        logger.error("Twilio API error: {} - Code: {}, More Info: {}", ex.getMessage(), ex.getCode(), ex.getMoreInfo());
        String userFriendlyMessage = "Could not send SMS. Please try again later.";
        if (ex.getMessage() != null && ex.getMessage().contains("unverified")) {
            userFriendlyMessage = "The destination phone number is unverified for trial account.";
        } else if (ex.getMessage() != null && ex.getMessage().contains("Authentication Error")) {
            userFriendlyMessage = "SMS service authentication failed. Please contact support.";
        }

        ApiResponse<Object> apiResponse = ApiResponse.builder()
                .success(false)
                .message(userFriendlyMessage)
                .errorCode(HttpStatus.SERVICE_UNAVAILABLE.value())
                .build();
        return new ResponseEntity<>(apiResponse, HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, WebRequest request) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage()));

        logger.warn("Validation error: {}", errors);
        ApiResponse<Object> apiResponse = ApiResponse.builder()
                .success(false)
                .message("Validation failed")
                .data(errors)
                .errorCode(HttpStatus.BAD_REQUEST.value())
                .build();
        return new ResponseEntity<>(apiResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleAllUncaughtException(Exception ex, WebRequest request) {
        logger.error("Unexpected error occurred: {}", ex.getMessage(), ex);
        ApiResponse<Object> apiResponse = ApiResponse.builder()
                .success(false)
                .message("An unexpected internal server error occurred.")
                .errorCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .build();
        return new ResponseEntity<>(apiResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}