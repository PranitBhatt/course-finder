package com.coursefinder.integration;

import com.coursefinder.connector.ConnectorCourse;
import com.coursefinder.entity.CourseSource;
import com.coursefinder.repository.CourseRepository;
import com.coursefinder.repository.projection.CourseSearchRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end search against a real PostgreSQL 16 with the real Flyway migrations.
 * External platforms are switched off, so only seeded data is used.
 * Skipped automatically when Docker is not available.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "app.coursera.enabled=false",
        "app.youtube.api-key=",
        "app.admin.enabled=true"
})
@AutoConfigureMockMvc
class CourseSearchIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @BeforeEach
    void seed() {
        jdbcTemplate.execute("TRUNCATE course, sync_run, topic_fetch RESTART IDENTITY");

        courseRepository.upsertAll(List.of(
                course(CourseSource.COURSERA, "c-ml", "Machine Learning",
                        "Supervised and unsupervised learning, by Andrew Ng.", null),
                course(CourseSource.COURSERA, "c-stats", "Statistics for Data Science",
                        "Probability and statistics that power machine learning models.", null),
                course(CourseSource.COURSERA, "c-cook", "Italian Cooking Basics",
                        "Pasta, risotto and sauces.", null),
                course(CourseSource.YOUTUBE, "y-ml", "Machine Learning Full Course for Beginners",
                        "12 hour course.", true),
                course(CourseSource.YOUTUBE, "y-py", "Python Full Course",
                        "Learn Python programming from scratch.", true)
        ), now);
    }

    private static ConnectorCourse course(CourseSource source, String id, String title,
                                          String description, Boolean free) {
        return new ConnectorCourse(source, id, title, description, "https://example.com/" + id,
                null, "Provider", "en", null, null, free,
                source == CourseSource.COURSERA ? new BigDecimal("4.80") : null);
    }

    private Page<CourseSearchRow> search(String q, CourseSource source, Boolean free) {
        return courseRepository.fullTextSearch(q, source, free, PageRequest.of(0, 20));
    }

    @Test
    void titleMatchesRankAboveDescriptionMatches() {
        Page<CourseSearchRow> page = search("machine learning", null, null);

        assertThat(page.getContent()).extracting(CourseSearchRow::externalId)
                .containsExactlyInAnyOrder("c-ml", "y-ml", "c-stats");
        // "Statistics..." only mentions machine learning in its description, so it ranks last.
        assertThat(page.getContent().getLast().externalId()).isEqualTo("c-stats");
        assertThat(page.getContent().getFirst().rank()).isGreaterThan(page.getContent().getLast().rank());
    }

    @Test
    void stemmingMatchesWordVariants() {
        assertThat(search("learn", null, null).getContent()).extracting(CourseSearchRow::externalId)
                .contains("c-ml", "y-ml", "y-py");
    }

    @Test
    void filtersBySourceAndFree() {
        assertThat(search("machine learning", CourseSource.YOUTUBE, null).getContent())
                .extracting(CourseSearchRow::externalId).containsExactly("y-ml");

        assertThat(search("machine learning", null, true).getContent())
                .extracting(CourseSearchRow::externalId).containsExactly("y-ml");

        // Coursera's is_free is NULL (unknown), so it matches neither free=true nor free=false.
        assertThat(search("machine learning", null, false).getContent()).isEmpty();
    }

    @Test
    void paginatesWithCorrectTotals() {
        Page<CourseSearchRow> first = courseRepository.fullTextSearch("machine learning", null, null,
                PageRequest.of(0, 2));
        Page<CourseSearchRow> second = courseRepository.fullTextSearch("machine learning", null, null,
                PageRequest.of(1, 2));

        assertThat(first.getTotalElements()).isEqualTo(3);
        assertThat(first.getTotalPages()).isEqualTo(2);
        assertThat(first.getContent()).hasSize(2);
        assertThat(second.getContent()).hasSize(1);
    }

    @Test
    void searchEndpointFallsBackToFuzzyMatchingForTypos() throws Exception {
        mockMvc.perform(get("/api/courses/search").param("q", "machne lerning"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fuzzyMatch").value(true))
                .andExpect(jsonPath("$.totalElements").value(greaterThan(0)))
                .andExpect(jsonPath("$.content[0].title").value("Machine Learning"));
    }

    @Test
    void fuzzySearchToleratesTyposButIgnoresUnrelatedTitles() {
        Page<CourseSearchRow> typo = courseRepository.fuzzyTitleSearch("pyton", null, null, PageRequest.of(0, 20));
        assertThat(typo.getContent()).extracting(CourseSearchRow::externalId).containsExactly("y-py");

        Page<CourseSearchRow> twoTypos = courseRepository.fuzzyTitleSearch("machne lerning", null, null,
                PageRequest.of(0, 20));
        assertThat(twoTypos.getContent()).extracting(CourseSearchRow::externalId)
                .containsExactly("c-ml", "y-ml")
                .doesNotContain("c-cook", "c-stats");
    }

    @Test
    void searchEndpointReturnsRankedPage() throws Exception {
        mockMvc.perform(get("/api/courses/search").param("q", "python").param("source", "youtube"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fuzzyMatch").value(false))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].externalId").doesNotExist())
                .andExpect(jsonPath("$.content[0].sourceName").value("YouTube"))
                .andExpect(jsonPath("$.content[0].free").value(true));
    }

    @Test
    void upsertUpdatesExistingRowInsteadOfDuplicating() {
        courseRepository.upsertAll(List.of(course(CourseSource.COURSERA, "c-ml", "Machine Learning (2026 edition)",
                "Updated.", null)), now);

        assertThat(courseRepository.countBySource(CourseSource.COURSERA)).isEqualTo(3);
        assertThat(search("2026 edition", null, null).getContent())
                .extracting(CourseSearchRow::externalId).containsExactly("c-ml");
    }

    @Test
    void deleteNotSyncedSinceRemovesOnlyStaleCourses() {
        Instant nextSync = now.plusSeconds(60);
        courseRepository.upsertAll(List.of(course(CourseSource.COURSERA, "c-ml", "Machine Learning",
                "Still in the catalog.", null)), nextSync);

        int removed = courseRepository.deleteNotSyncedSince(CourseSource.COURSERA, nextSync);

        assertThat(removed).isEqualTo(2);
        assertThat(courseRepository.countBySource(CourseSource.COURSERA)).isEqualTo(1);
        assertThat(courseRepository.countBySource(CourseSource.YOUTUBE)).isEqualTo(2);
    }

    @Test
    void sourcesEndpointReportsCounts() throws Exception {
        mockMvc.perform(get("/api/sources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].source").value("COURSERA"))
                .andExpect(jsonPath("$[0].syncMode").value("FULL_CATALOG"))
                .andExpect(jsonPath("$[0].courseCount").value(3))
                .andExpect(jsonPath("$[1].source").value("YOUTUBE"))
                .andExpect(jsonPath("$[1].syncMode").value("ON_DEMAND"))
                .andExpect(jsonPath("$[1].enabled").value(false))
                .andExpect(jsonPath("$[1].courseCount").value(2));
    }

    @Test
    void getByIdReturnsCourseOr404() throws Exception {
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM course WHERE external_id = 'c-ml'", Long.class);

        mockMvc.perform(get("/api/courses/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Machine Learning"))
                .andExpect(jsonPath("$.rating").value(4.8));

        mockMvc.perform(get("/api/courses/{id}", 999_999))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminSyncRejectsOnDemandSource() throws Exception {
        mockMvc.perform(post("/api/admin/sync/youtube"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Sync not supported"));
    }
}
