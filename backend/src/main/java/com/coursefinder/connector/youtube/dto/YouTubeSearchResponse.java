package com.coursefinder.connector.youtube.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Response of {@code GET /search?part=snippet&type=playlist}. Costs 100 quota units. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record YouTubeSearchResponse(List<Item> items) {

    public List<Item> itemsOrEmpty() {
        return items == null ? List.of() : items;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(Id id, Snippet snippet) {

        public String playlistId() {
            return id == null ? null : id.playlistId();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Id(String kind, String playlistId) {
    }

    /** Note: title/description here are HTML-escaped and the description is truncated. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Snippet(
            String title,
            String description,
            String channelTitle,
            YouTubeThumbnails thumbnails
    ) {
    }
}
