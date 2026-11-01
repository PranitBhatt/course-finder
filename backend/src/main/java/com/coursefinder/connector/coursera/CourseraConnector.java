package com.coursefinder.connector.coursera;

import com.coursefinder.config.HttpClientConfig;
import com.coursefinder.config.properties.CourseraProperties;
import com.coursefinder.connector.CatalogConnector;
import com.coursefinder.connector.ConnectorCourse;
import com.coursefinder.connector.ConnectorException;
import com.coursefinder.connector.coursera.dto.CourseraCourse;
import com.coursefinder.connector.coursera.dto.CourseraPartner;
import com.coursefinder.connector.coursera.dto.CourseraResponse;
import com.coursefinder.entity.CourseSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Pulls the Coursera catalog from the unofficial public endpoint {@code /api/courses.v1}.
 * <p>
 * The endpoint is undocumented and may change, so every failure is wrapped in a
 * {@link ConnectorException}, transient errors are retried, and malformed elements are skipped.
 */
@Component
public class CourseraConnector implements CatalogConnector {

    private static final Logger log = LoggerFactory.getLogger(CourseraConnector.class);

    static final String COURSES_PATH = "/api/courses.v1";
    static final String FIELDS =
            "name,slug,description,photoUrl,primaryLanguages,workload,partnerIds,partners.v1(name)";
    static final String COURSE_URL_PREFIX = "https://www.coursera.org/learn/";

    private final RestClient restClient;
    private final CourseraProperties properties;

    public CourseraConnector(@Qualifier(HttpClientConfig.COURSERA_CLIENT) RestClient restClient,
                             CourseraProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public CourseSource source() {
        return CourseSource.COURSERA;
    }

    @Override
    public boolean isEnabled() {
        return properties.enabled();
    }

    @Override
    public CatalogFetchResult fetchCatalog(Consumer<List<ConnectorCourse>> pageHandler) {
        int start = 0;
        int pagesFetched = 0;
        int coursesFetched = 0;

        while (pagesFetched < properties.maxPages()) {
            CourseraResponse page = fetchPageWithRetry(start);
            pagesFetched++;

            List<ConnectorCourse> courses = toConnectorCourses(page);
            if (!courses.isEmpty()) {
                pageHandler.accept(courses);
                coursesFetched += courses.size();
            }

            Integer next = page.nextStart();
            boolean lastPage = next == null || next <= start || page.elementsOrEmpty().isEmpty();
            if (lastPage) {
                log.info("Coursera catalog fetched: {} pages, {} courses", pagesFetched, coursesFetched);
                return new CatalogFetchResult(pagesFetched, coursesFetched, true);
            }
            start = next;
        }

        log.warn("Coursera fetch stopped at page cap ({} pages, {} courses); result is incomplete",
                pagesFetched, coursesFetched);
        return new CatalogFetchResult(pagesFetched, coursesFetched, false);
    }

    // ---------------------------------------------------------------------
    // HTTP
    // ---------------------------------------------------------------------

    private CourseraResponse fetchPageWithRetry(int start) {
        int attempt = 0;
        while (true) {
            try {
                return fetchPage(start);
            } catch (ResourceAccessException | HttpServerErrorException e) {
                // Network problem or 5xx: worth retrying.
                if (attempt >= properties.maxRetries()) {
                    throw new ConnectorException(source(),
                            "Coursera request failed at start=%d after %d attempts: %s"
                                    .formatted(start, attempt + 1, e.getMessage()), e);
                }
                attempt++;
                log.warn("Coursera request failed at start={} (attempt {}), retrying: {}",
                        start, attempt, e.getMessage());
                sleep(properties.retryBackoff().toMillis() * attempt);
            } catch (RestClientException e) {
                // 4xx or unreadable JSON: retrying won't help.
                throw new ConnectorException(source(),
                        "Coursera request failed at start=%d: %s".formatted(start, e.getMessage()), e);
            }
        }
    }

    private CourseraResponse fetchPage(int start) {
        CourseraResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(COURSES_PATH)
                        .queryParam("start", start)
                        .queryParam("limit", properties.pageSize())
                        .queryParam("fields", FIELDS)
                        .queryParam("includes", "partnerIds")
                        .build())
                .retrieve()
                .body(CourseraResponse.class);

        if (response == null) {
            throw new ConnectorException(source(), "Coursera returned an empty body at start=" + start);
        }
        return response;
    }

    private static void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConnectorException(CourseSource.COURSERA, "Interrupted while retrying", e);
        }
    }

    // ---------------------------------------------------------------------
    // Mapping
    // ---------------------------------------------------------------------

    private List<ConnectorCourse> toConnectorCourses(CourseraResponse page) {
        Map<String, String> partnerNames = page.partnersOrEmpty().stream()
                .filter(partner -> partner.id() != null && StringUtils.hasText(partner.name()))
                .collect(Collectors.toMap(CourseraPartner::id, CourseraPartner::name, (a, b) -> a));

        List<ConnectorCourse> courses = new ArrayList<>();
        for (CourseraCourse element : page.elementsOrEmpty()) {
            if (!isUsable(element)) {
                log.debug("Skipping Coursera element without id/slug/name: {}", element);
                continue;
            }
            courses.add(toConnectorCourse(element, partnerNames::get));
        }
        return courses;
    }

    private static boolean isUsable(CourseraCourse element) {
        return element != null
                && StringUtils.hasText(element.id())
                && StringUtils.hasText(element.slug())
                && StringUtils.hasText(element.name());
    }

    private ConnectorCourse toConnectorCourse(CourseraCourse element, Function<String, String> partnerName) {
        String provider = element.partnerIds() == null ? null : element.partnerIds().stream()
                .map(partnerName)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.collectingAndThen(Collectors.joining(", "), joined ->
                        joined.isEmpty() ? null : joined));

        String language = element.primaryLanguages() == null || element.primaryLanguages().isEmpty()
                ? null
                : element.primaryLanguages().getFirst();

        return new ConnectorCourse(
                source(),
                element.id(),
                element.name().trim(),
                blankToNull(element.description()),
                COURSE_URL_PREFIX + element.slug(),
                blankToNull(element.photoUrl()),
                provider,
                language,
                null,                           // level: not exposed by this endpoint
                blankToNull(element.workload()),
                null,                           // free: audit is often free, certificate is paid
                null                            // rating: not exposed by this endpoint
        );
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
