package com.coursefinder.connector.youtube.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Response of {@code GET /playlists?part=snippet,contentDetails&id=...}. Costs 1 quota unit.
 * Gives the full description, language and video count that search results lack.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record YouTubePlaylistResponse(List<Item> items) {

    public List<Item> itemsOrEmpty() {
        return items == null ? List.of() : items;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(String id, Snippet snippet, ContentDetails contentDetails) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Snippet(
            String title,
            String description,
            String channelTitle,
            String defaultLanguage,
            YouTubeThumbnails thumbnails
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ContentDetails(Integer itemCount) {
    }
}
