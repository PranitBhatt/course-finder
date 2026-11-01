package com.coursefinder.support;

import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Loads JSON fixtures from {@code src/test/resources/fixtures}. */
public final class Fixtures {

    private Fixtures() {
    }

    public static String read(String path) {
        try {
            return new ClassPathResource("fixtures/" + path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Missing fixture: " + path, e);
        }
    }
}
