package com.boeing.user.exception;

public class DuplicateResourceException extends BusinessLogicException {
    public DuplicateResourceException(String resourceName, String fieldName, String fieldValue) {
        super(String.format("%s already exists with %s : '%s'", resourceName, fieldName, fieldValue));
    }
}
