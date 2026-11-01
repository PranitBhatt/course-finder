package com.coursefinder.connector.youtube;

import com.coursefinder.config.properties.YouTubeProperties;
import com.coursefinder.connector.ConnectorCourse;
import com.coursefinder.connector.ConnectorException;
import com.coursefinder.connector.QuotaExceededException;
import com.coursefinder.entity.CourseSource;
import com.coursefinder.support.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class YouTubeConnectorTest {

    private static final String BASE_URL = "https://youtube.test/v3";
    private static final String API_KEY = "test-key";

    private MockRestServiceServer server;
    private YouTubeQuotaGuard quotaGuard;

    private YouTubeConnector connector(String apiKey, int dailyQuota) {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        YouTubeProperties properties = new YouTubeProperties(
                apiKey, BASE_URL, 25, Duration.ofDays(7), dailyQuota, Duration.ofSeconds(1), Duration.ofSeconds(1));
        quotaGuard = new YouTubeQuotaGuard(dailyQuota, Clock.systemUTC());
        return new YouTubeConnector(builder.build(), properties, quotaGuard);
    }

    private void expectSearch(String fixture) {
        server.expect(requestTo(startsWith(BASE_URL + "/search")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(YouTubeConnector.API_KEY_HEADER, API_KEY))
                .andExpect(queryParam("type", "playlist"))
                .andExpect(queryParam("q", "machine%20learning%20full%20course"))
                .andRespond(withSuccess(Fixtures.read(fixture), MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("searches playlists, then enriches them with playlist details")
    void searchesAndEnrichesPlaylists() {
        YouTubeConnector connector = connector(API_KEY, 10_000);
        expectSearch("youtube/search.json");
        server.expect(requestTo(startsWith(BASE_URL + "/playlists")))
                .andExpect(header(YouTubeConnector.API_KEY_HEADER, API_KEY))
                .andExpect(queryParam("id", "PL_ML_1,PL_PY_2"))
                .andRespond(withSuccess(Fixtures.read("youtube/playlists.json"), MediaType.APPLICATION_JSON));

        List<ConnectorCourse> courses = connector.searchByTopic("machine learning");

        server.verify();
        assertThat(courses).hasSize(2);

        ConnectorCourse ml = courses.getFirst();
        assertThat(ml.source()).isEqualTo(CourseSource.YOUTUBE);
        assertThat(ml.externalId()).isEqualTo("PL_ML_1");
        assertThat(ml.url()).isEqualTo("https://www.youtube.com/playlist?list=PL_ML_1");
        assertThat(ml.description()).startsWith("The complete, untruncated");
        assertThat(ml.thumbnailUrl()).isEqualTo("https://i.ytimg.com/ml/maxres.jpg");
        assertThat(ml.provider()).isEqualTo("freeCodeCamp.org");
        assertThat(ml.language()).isEqualTo("en");
        assertThat(ml.durationText()).isEqualTo("42 videos");
        assertThat(ml.free()).isTrue();

        // No details returned for the second playlist: falls back to the (HTML-escaped) search snippet.
        ConnectorCourse python = courses.get(1);
        assertThat(python.title()).isEqualTo("Python & Data Science 'Full' Course");
        assertThat(python.description()).isEqualTo("Pandas & NumPy");
        assertThat(python.thumbnailUrl()).isEqualTo("https://i.ytimg.com/py/medium.jpg");
        assertThat(python.durationText()).isNull();

        assertThat(quotaGuard.remainingUnits()).isEqualTo(10_000 - 100 - 1);
    }

    @Test
    @DisplayName("still returns search results when the details call fails")
    void detailsFailureFallsBackToSearchSnippets() {
        YouTubeConnector connector = connector(API_KEY, 10_000);
        expectSearch("youtube/search.json");
        server.expect(requestTo(startsWith(BASE_URL + "/playlists"))).andRespond(withServerError());

        List<ConnectorCourse> courses = connector.searchByTopic("machine learning");

        assertThat(courses).hasSize(2);
        assertThat(courses.getFirst().description()).isEqualTo("Truncated description...");
        assertThat(courses.getFirst().thumbnailUrl()).isEqualTo("https://i.ytimg.com/ml/high.jpg");
    }

    @Test
    @DisplayName("skips the details call when search finds nothing")
    void emptySearchMakesNoDetailsCall() {
        YouTubeConnector connector = connector(API_KEY, 10_000);
        server.expect(requestTo(startsWith(BASE_URL + "/search")))
                .andRespond(withSuccess("{\"items\": []}", MediaType.APPLICATION_JSON));

        assertThat(connector.searchByTopic("machine learning")).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("is disabled and makes no calls when the API key is missing")
    void disabledWithoutApiKey() {
        YouTubeConnector connector = connector("", 10_000);

        assertThat(connector.isEnabled()).isFalse();
        assertThatThrownBy(() -> connector.searchByTopic("java"))
                .isInstanceOf(ConnectorException.class)
                .hasMessageContaining("not configured");
        server.verify();
    }

    @Test
    @DisplayName("refuses to call the API once our own daily budget is used up")
    void ownBudgetExhaustedSkipsHttp() {
        YouTubeConnector connector = connector(API_KEY, 50);

        assertThatThrownBy(() -> connector.searchByTopic("java"))
                .isInstanceOf(QuotaExceededException.class);
        server.verify();
    }

    @Test
    @DisplayName("a 403 quotaExceeded from YouTube marks the budget as exhausted")
    void quotaExceededFromApi() {
        YouTubeConnector connector = connector(API_KEY, 10_000);
        server.expect(requestTo(startsWith(BASE_URL + "/search")))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Fixtures.read("youtube/quota-exceeded.json")));

        assertThatThrownBy(() -> connector.searchByTopic("machine learning"))
                .isInstanceOf(QuotaExceededException.class);
        assertThat(quotaGuard.remainingUnits()).isZero();
    }

    @Test
    @DisplayName("other client errors (e.g. bad key) become a plain ConnectorException")
    void badRequestIsConnectorException() {
        YouTubeConnector connector = connector(API_KEY, 10_000);
        server.expect(requestTo(startsWith(BASE_URL + "/search")))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\": {\"errors\": [{\"reason\": \"keyInvalid\"}]}}"));

        assertThatThrownBy(() -> connector.searchByTopic("machine learning"))
                .isInstanceOf(ConnectorException.class)
                .isNotInstanceOf(QuotaExceededException.class)
                .hasMessageContaining("400")
                .hasMessageNotContaining(API_KEY);
    }
}
