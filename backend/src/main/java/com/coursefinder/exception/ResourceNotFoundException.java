package com.coursefinder.exception;

/** Mapped to 404 by {@link GlobalExceptionHandler}. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, Object id) {
        super("%s with id %s not found".formatted(resource, id));
    }
}
