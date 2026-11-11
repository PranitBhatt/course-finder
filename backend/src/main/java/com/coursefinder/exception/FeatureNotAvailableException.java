package com.coursefinder.exception;

import com.coursefinder.service.Feature;

/** The user is logged in but not entitled to the feature. Mapped to 403 by {@link GlobalExceptionHandler}. */
public class FeatureNotAvailableException extends RuntimeException {

    public FeatureNotAvailableException(Feature feature) {
        super("Your account doesn't include %s".formatted(feature.name().toLowerCase().replace('_', ' ')));
    }
}
