package com.coursefinder.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The string must be at most {@link #value()} bytes once encoded as UTF-8.
 * <p>
 * Different from {@code @Size}, which counts characters: "é" is 1 character but 2 bytes,
 * an emoji is 2 characters (Java counts UTF-16 units) but 4 bytes.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MaxUtf8BytesValidator.class)
public @interface MaxUtf8Bytes {

    int value();

    String message() default "is too long";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
