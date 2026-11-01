package com.coursefinder.connector.youtube.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.stream.Stream;

/** The {@code snippet.thumbnails} object shared by search and playlist responses. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record YouTubeThumbnails(
        Thumbnail maxres,
        Thumbnail standard,
        Thumbnail high,
        Thumbnail medium,
        @JsonProperty("default") Thumbnail defaultThumbnail
) {

    /** @return URL of the largest available thumbnail, or null */
    public String bestUrl() {
        return Stream.of(maxres, standard, high, medium, defaultThumbnail)
                .filter(thumbnail -> thumbnail != null && thumbnail.url() != null)
                .map(Thumbnail::url)
                .findFirst()
                .orElse(null);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Thumbnail(String url, Integer width, Integer height) {
    }
}
