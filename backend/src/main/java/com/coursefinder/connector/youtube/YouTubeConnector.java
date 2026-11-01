package com.coursefinder.connector.youtube;

import com.coursefinder.config.HttpClientConfig;
import com.coursefinder.config.properties.YouTubeProperties;
import com.coursefinder.connector.ConnectorCourse;
import com.coursefinder.connector.ConnectorException;
import com.coursefinder.connector.QuotaExceededException;
import com.coursefinder.connector.TopicSearchConnector;
import com.coursefinder.connector.youtube.dto.YouTubePlaylistResponse;
import com.coursefinder.connector.youtube.dto.YouTubeSearchResponse;
import com.coursefinder.entity.CourseSource;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.HtmlUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Finds YouTube playlists for "&lt;topic&gt; full course" via the YouTube Data API v3.
 * <p>
 * Two calls per topic: {@code search.list} (100 units) to find playlists, then one batched
 * {@code playlists.list} (1 unit) for full descriptions and video counts.
 * The API key is sent in the {@code X-Goog-Api-Key} header so it never appears in URLs or logs.
 */
@Component
public class YouTubeConnector implements TopicSearchConnector {

    private static final Logger log = LoggerFactory.getLogger(YouTubeConnector.class);

    static final int SEARCH_COST_UNITS = 100;
    static final int PLAYLISTS_COST_UNITS = 1;
    static final String API_KEY_HEADER = "X-Goog-Api-Key";
    static final String PLAYLIST_URL_PREFIX = "https://www.youtube.com/playlist?list=";
    static final String QUERY_SUFFIX = " full course";

    private final RestClient restClient;
    private final YouTubeProperties properties;
    private final YouTubeQuotaGuard quotaGuard;

    public YouTubeConnector(@Qualifier(HttpClientConfig.YOUTUBE_CLIENT) RestClient restClient,
                            YouTubeProperties properties,
                            YouTubeQuotaGuard quotaGuard) {
        this.restClient = restClient;
        this.properties = properties;
        this.quotaGuard = quotaGuard;
    }

    @PostConstruct
    void logStatus() {
        if (!isEnabled()) {
            log.warn("YOUTUBE_API_KEY is not set - YouTube connector is disabled");
        }
    }

    @Override
    public CourseSource source() {
        return CourseSource.YOUTUBE;
    }

    @Override
    public boolean isEnabled() {
        return properties.hasApiKey();
    }

    @Override
    public List<ConnectorCourse> searchByTopic(String topic) {
        if (!isEnabled()) {
            throw new ConnectorException(source(), "YouTube API key is not configured");
        }
        if (!quotaGuard.tryConsume(SEARCH_COST_UNITS)) {
            throw new QuotaExceededException(source(), "Daily YouTube quota budget used up");
        }

        List<YouTubeSearchResponse.Item> searchItems = searchPlaylists(topic).itemsOrEmpty().stream()
                .filter(item -> StringUtils.hasText(item.playlistId()) && item.snippet() != null)
                .toList();
        if (searchItems.isEmpty()) {
            return List.of();
        }

        Map<String, YouTubePlaylistResponse.Item> details = fetchPlaylistDetails(searchItems);

        List<ConnectorCourse> courses = new ArrayList<>(searchItems.size());
        for (YouTubeSearchResponse.Item item : searchItems) {
            courses.add(toConnectorCourse(item, details.get(item.playlistId())));
        }
        log.debug("YouTube returned {} playlists for '{}'", courses.size(), topic);
        return courses;
    }

    // ---------------------------------------------------------------------
    // HTTP
    // ---------------------------------------------------------------------

    private YouTubeSearchResponse searchPlaylists(String topic) {
        YouTubeSearchResponse response = execute("search", () -> restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/search")
                        .queryParam("part", "snippet")
                        .queryParam("type", "playlist")
                        .queryParam("q", topic + QUERY_SUFFIX)
                        .queryParam("maxResults", properties.maxResults())
                        .build())
                .header(API_KEY_HEADER, properties.apiKey())
                .retrieve()
                .body(YouTubeSearchResponse.class));

        return response == null ? new YouTubeSearchResponse(List.of()) : response;
    }

    /**
     * Enrichment is optional: if it fails or the budget is gone we still return
     * the search results, just with less detail.
     */
    private Map<String, YouTubePlaylistResponse.Item> fetchPlaylistDetails(List<YouTubeSearchResponse.Item> items) {
        if (!quotaGuard.tryConsume(PLAYLISTS_COST_UNITS)) {
            return Map.of();
        }

        String ids = items.stream()
                .map(YouTubeSearchResponse.Item::playlistId)
                .collect(Collectors.joining(","));
        try {
            YouTubePlaylistResponse response = execute("playlists", () -> restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/playlists")
                            .queryParam("part", "snippet,contentDetails")
                            .queryParam("id", ids)
                            .queryParam("maxResults", items.size())
                            .build())
                    .header(API_KEY_HEADER, properties.apiKey())
                    .retrieve()
                    .body(YouTubePlaylistResponse.class));

            return response == null ? Map.of() : response.itemsOrEmpty().stream()
                    .filter(item -> item.id() != null)
                    .collect(Collectors.toMap(YouTubePlaylistResponse.Item::id, Function.identity(), (a, b) -> a));
        } catch (QuotaExceededException e) {
            throw e;
        } catch (ConnectorException e) {
            log.warn("YouTube playlist details unavailable, using search snippets only: {}", e.getMessage());
            return Map.of();
        }
    }

    private <T> T execute(String operation, Supplier<T> call) {
        try {
            return call.get();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.FORBIDDEN && isQuotaError(e)) {
                quotaGuard.markExhausted();
                throw new QuotaExceededException(source(), "YouTube reported quota exceeded");
            }
            throw new ConnectorException(source(),
                    "YouTube %s failed with status %s".formatted(operation, e.getStatusCode().value()), e);
        } catch (RestClientException e) {
            throw new ConnectorException(source(),
                    "YouTube %s failed: %s".formatted(operation, e.getMessage()), e);
        }
    }

    private static boolean isQuotaError(HttpClientErrorException e) {
        String body = e.getResponseBodyAsString();
        return body.contains("quotaExceeded") || body.contains("dailyLimitExceeded");
    }

    // ---------------------------------------------------------------------
    // Mapping
    // ---------------------------------------------------------------------

    private ConnectorCourse toConnectorCourse(YouTubeSearchResponse.Item searchItem,
                                              YouTubePlaylistResponse.Item detail) {
        YouTubeSearchResponse.Snippet searchSnippet = searchItem.snippet();
        YouTubePlaylistResponse.Snippet detailSnippet = detail == null ? null : detail.snippet();

        // Prefer playlist details (full, unescaped text); fall back to the search snippet.
        String title = detailSnippet != null && StringUtils.hasText(detailSnippet.title())
                ? detailSnippet.title()
                : HtmlUtils.htmlUnescape(Objects.requireNonNullElse(searchSnippet.title(), "Untitled playlist"));

        String description = detailSnippet != null && StringUtils.hasText(detailSnippet.description())
                ? detailSnippet.description()
                : unescapeOrNull(searchSnippet.description());

        String thumbnailUrl = detailSnippet != null && detailSnippet.thumbnails() != null
                ? detailSnippet.thumbnails().bestUrl()
                : searchSnippet.thumbnails() == null ? null : searchSnippet.thumbnails().bestUrl();

        String language = detailSnippet == null ? null : detailSnippet.defaultLanguage();

        Integer videoCount = detail == null || detail.contentDetails() == null
                ? null
                : detail.contentDetails().itemCount();
        String durationText = videoCount == null ? null : videoCount + (videoCount == 1 ? " video" : " videos");

        return new ConnectorCourse(
                source(),
                searchItem.playlistId(),
                title.trim(),
                description == null ? null : description.trim(),
                PLAYLIST_URL_PREFIX + searchItem.playlistId(),
                thumbnailUrl,
                searchSnippet.channelTitle(),
                language,
                null,                   // level: unknown
                durationText,
                Boolean.TRUE,           // YouTube playlists are free to watch
                null                    // rating: YouTube has no course rating
        );
    }

    private static String unescapeOrNull(String value) {
        return StringUtils.hasText(value) ? HtmlUtils.htmlUnescape(value) : null;
    }
}
