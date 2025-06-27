package com.boeing.bookingservice.exception;

public class SagaProcessingException extends RuntimeException {
    public SagaProcessingException(String message) {
        super(message);
    }
    public SagaProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}