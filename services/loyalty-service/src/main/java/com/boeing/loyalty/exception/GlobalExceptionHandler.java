package com.boeing.loyalty.exception;

import com.boeing.loyalty.dto.APIResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<APIResponse> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();

        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(APIResponse.builder()
                        .statusCode(HttpStatus.BAD_REQUEST.value())
                        .error(errors)
                        .build());
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<APIResponse> badRequestException(BadRequestException e) {
        return ResponseEntity
                .status(BAD_REQUEST)
                .body(
                        APIResponse.builder()
                                .statusCode(BAD_REQUEST.value())
                                .error(e.getMessage())
                                .build()
                );
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<APIResponse> runtimeException(RuntimeException e) {
        return ResponseEntity
                .status(INTERNAL_SERVER_ERROR)
                .body(
                        APIResponse.builder()
                                .statusCode(INTERNAL_SERVER_ERROR.value())
                                .error(e.getMessage())
                                .build()
                );
    }

}
