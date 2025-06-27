package com.boeing.aircraftservice.exception;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class ElementNotFoundException extends RuntimeException {

    private String message;

    public ElementNotFoundException(String message) {
        this.message = message;
    }

}
