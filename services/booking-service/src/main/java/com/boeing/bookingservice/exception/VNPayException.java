package com.boeing.bookingservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class VNPayException extends RuntimeException {
    private final String responseCode;

    public VNPayException(String message) {
        super(message);
        this.responseCode = null;
    }

    public VNPayException(String message, String responseCode) {
        super(message);
        this.responseCode = responseCode;
    }

    public VNPayException(String message, Throwable cause) {
        super(message, cause);
        this.responseCode = null;
    }

    public String getResponseCode() {
        return responseCode;
    }
}
