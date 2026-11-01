package com.coursefinder.connector.coursera;

import com.coursefinder.config.properties.CourseraProperties;
import com.coursefinder.connector.CatalogConnector.CatalogFetchResult;
import com.coursefinder.connector.ConnectorCourse;
import com.coursefinder.connector.ConnectorException;
import com.coursefinder.entity.CourseSource;
import com.coursefinder.support.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CourseraConnectorTest {

    private static final String BASE_URL = "https://api.coursera.test";

    private MockRestServiceServer server;

    private CourseraConnector connector(int maxPages, int maxRetries) {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        CourseraProperties properties = new CourseraProperties(
                true, BASE_URL, 2, maxPages, maxRetries, Duration.ZERO,
                "0 0 3 * * *", false, Duration.ofSeconds(1), Duration.ofSeconds(1));
        return new CourseraConnector(builder.build(), properties);
    }

    private void expectPage(String start, String fixture) {
        server.expect(requestTo(startsWith(BASE_URL + CourseraConnector.COURSES_PATH)))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("start", start))
                .andExpect(queryParam("limit", "2"))
                .andExpect(queryParam("includes", "partnerIds"))
                .andRespond(withSuccess(Fixtures.read(fixture), MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("pages through the whole catalog and maps every field")
    void fetchesAllPagesAndMapsFields() {
        CourseraConnector connector = connector(100, 0);
        expectPage("0", "coursera/page-1.json");
        expectPage("2", "coursera/page-2.json");

        List<List<ConnectorCourse>> pages = new ArrayList<>();
        CatalogFetchResult result = connector.fetchCatalog(pages::add);

        server.verify();
        assertThat(result).isEqualTo(new CatalogFetchResult(2, 3, true));
        assertThat(pages).hasSize(2);

        ConnectorCourse ml = pages.getFirst().getFirst();
        assertThat(ml.source()).isEqualTo(CourseSource.COURSERA);
        assertThat(ml.externalId()).isEqualTo("Gtv4Xb1-EeS-ViIACwYKVQ");
        assertThat(ml.title()).isEqualTo("Machine Learning");
        assertThat(ml.url()).isEqualTo("https://www.coursera.org/learn/machine-learning");
        assertThat(ml.thumbnailUrl()).isEqualTo("https://images.example.com/ml.png");
        assertThat(ml.provider()).isEqualTo("Stanford University, DeepLearning.AI");
        assertThat(ml.language()).isEqualTo("en");
        assertThat(ml.durationText()).isEqualTo("60 hours");
        assertThat(ml.free()).isNull();
        assertThat(ml.rating()).isNull();
    }

    @Test
    @DisplayName("trims text, turns blank fields into null and skips unusable elements")
    void normalizesBlankFieldsAndSkipsBrokenElements() {
        CourseraConnector connector = connector(100, 0);
        expectPage("0", "coursera/page-1.json");
        expectPage("2", "coursera/page-2.json");

        List<ConnectorCourse> all = new ArrayList<>();
        connector.fetchCatalog(all::addAll);

        ConnectorCourse python = all.get(1);
        assertThat(python.title()).isEqualTo("Python for Everybody");
        assertThat(python.description()).isNull();
        assertThat(python.thumbnailUrl()).isNull();
        assertThat(python.durationText()).isNull();
        assertThat(python.language()).isNull();

        assertThat(all).extracting(ConnectorCourse::externalId)
                .containsExactly("Gtv4Xb1-EeS-ViIACwYKVQ", "abc123", "xyz789")
                .doesNotContain("broken-no-slug");
    }

    @Test
    @DisplayName("retries a 5xx response and then continues")
    void retriesServerErrorThenSucceeds() {
        CourseraConnector connector = connector(100, 2);
        server.expect(requestTo(startsWith(BASE_URL))).andRespond(withServerError());
        expectPage("0", "coursera/page-2.json");

        CatalogFetchResult result = connector.fetchCatalog(page -> { });

        server.verify();
        assertThat(result.complete()).isTrue();
        assertThat(result.coursesFetched()).isEqualTo(1);
    }

    @Test
    @DisplayName("gives up with ConnectorException once retries are used up")
    void failsAfterRetriesExhausted() {
        CourseraConnector connector = connector(100, 2);
        server.expect(ExpectedCount.times(3), requestTo(startsWith(BASE_URL)))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> connector.fetchCatalog(page -> { }))
                .isInstanceOf(ConnectorException.class)
                .hasMessageContaining("after 3 attempts");
        server.verify();
    }

    @Test
    @DisplayName("does not retry 4xx responses")
    void doesNotRetryClientErrors() {
        CourseraConnector connector = connector(100, 2);
        server.expect(ExpectedCount.once(), requestTo(startsWith(BASE_URL)))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> connector.fetchCatalog(page -> { }))
                .isInstanceOf(ConnectorException.class);
        server.verify();
    }

    @Test
    @DisplayName("wraps malformed JSON in ConnectorException")
    void malformedJsonThrowsConnectorException() {
        CourseraConnector connector = connector(100, 0);
        server.expect(requestTo(startsWith(BASE_URL)))
                .andRespond(withSuccess("{ not json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> connector.fetchCatalog(page -> { }))
                .isInstanceOf(ConnectorException.class)
                .extracting(e -> ((ConnectorException) e).getSource())
                .isEqualTo(CourseSource.COURSERA);
    }

    @Test
    @DisplayName("stops at the page cap and reports the result as incomplete")
    void stopsAtPageCap() {
        CourseraConnector connector = connector(1, 0);
        expectPage("0", "coursera/page-1.json");

        CatalogFetchResult result = connector.fetchCatalog(page -> { });

        server.verify();
        assertThat(result.complete()).isFalse();
        assertThat(result.pagesFetched()).isEqualTo(1);
    }
}
