package com.coursefinder.integration;

import com.coursefinder.connector.ConnectorCourse;
import com.coursefinder.entity.CourseSource;
import com.coursefinder.repository.CourseRepository;
import com.coursefinder.support.TestJwtKeys;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Course progress ("My Learning") end to end: real HTTP, real tokens, real PostgreSQL.
 * Skipped automatically when Docker is not available.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "app.features.auth.enabled=true",
        "app.coursera.enabled=false",
        "app.youtube.api-key="
})
@AutoConfigureMockMvc
class ProgressIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) {
        TestJwtKeys.register(registry);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private CourseRepository courseRepository;

    private final Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    private long mlId;
    private long pyId;
    private long cookId;

    @BeforeEach
    void seed() {
        // CASCADE also empties user_course_progress, sessions and refresh tokens.
        jdbcTemplate.execute("TRUNCATE app_user, course, sync_run, topic_fetch RESTART IDENTITY CASCADE");
        courseRepository.upsertAll(List.of(
                course(CourseSource.COURSERA, "c-ml", "Machine Learning"),
                course(CourseSource.COURSERA, "c-cook", "Italian Cooking Basics"),
                course(CourseSource.YOUTUBE, "y-py", "Python Full Course")
        ), now);
        mlId = idOf("c-ml");
        cookId = idOf("c-cook");
        pyId = idOf("y-py");
    }

    // ---------------------------------------------------------------- auth

    @Test
    void everyEndpointNeedsALogin() throws Exception {
        mockMvc.perform(get("/api/me/progress")).andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mockMvc.perform(get("/api/me/progress/summary")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me/progress/lookup").param("courseIds", "1")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/me/progress/{id}", mlId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"PLANNED\"}")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/me/progress/{id}", mlId)).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- upsert + status rules

    @Test
    void upsertCreatesThenUpdatesTheSameRow() throws Exception {
        String token = register("ada@example.com");

        String created = upsert(token, mlId, "{\"status\": \"PLANNED\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PLANNED"))
                .andExpect(jsonPath("$.progressPercent").value(0))
                .andExpect(jsonPath("$.courseTitle").value("Machine Learning"))
                .andExpect(jsonPath("$.courseAvailable").value(true))
                .andReturn().getResponse().getContentAsString();

        String updated = upsert(token, mlId, "{\"status\": \"IN_PROGRESS\", \"progressPercent\": 40, \"notes\": \"week 2\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progressPercent").value(40))
                .andExpect(jsonPath("$.notes").value("week 2"))
                .andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(updated, "$.entryId")).isEqualTo(JsonPath.read(created, "$.entryId"));
        assertThat(countProgressRows()).isEqualTo(1);
    }

    @Test
    void sameRequestTwiceChangesNothing() throws Exception {
        String token = register("ada@example.com");
        String body = "{\"status\": \"COMPLETED\"}";

        String first = upsert(token, mlId, body).andReturn().getResponse().getContentAsString();
        String second = upsert(token, mlId, body).andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(second, "$.completedAt")).isEqualTo(JsonPath.read(first, "$.completedAt"));
        assertThat((String) JsonPath.read(second, "$.updatedAt")).isEqualTo(JsonPath.read(first, "$.updatedAt"));
    }

    @Test
    void statusRules() throws Exception {
        String token = register("ada@example.com");

        // IN_PROGRESS sets started_at.
        String inProgress = upsert(token, mlId, "{\"status\": \"IN_PROGRESS\", \"progressPercent\": 30}")
                .andExpect(jsonPath("$.startedAt").isNotEmpty())
                .andExpect(jsonPath("$.completedAt").isEmpty())
                .andReturn().getResponse().getContentAsString();
        String startedAt = JsonPath.read(inProgress, "$.startedAt");

        // COMPLETED: 100%, completed_at set, started_at kept.
        upsert(token, mlId, "{\"status\": \"COMPLETED\", \"progressPercent\": 50}")
                .andExpect(jsonPath("$.progressPercent").value(100))
                .andExpect(jsonPath("$.completedAt").isNotEmpty())
                .andExpect(jsonPath("$.startedAt").value(startedAt));

        // Moving away from COMPLETED clears completed_at.
        upsert(token, mlId, "{\"status\": \"IN_PROGRESS\", \"progressPercent\": 80}")
                .andExpect(jsonPath("$.completedAt").isEmpty())
                .andExpect(jsonPath("$.progressPercent").value(80));

        // PLANNED without a percent goes back to 0; with one, keeps it.
        upsert(token, mlId, "{\"status\": \"PLANNED\"}").andExpect(jsonPath("$.progressPercent").value(0));
        upsert(token, mlId, "{\"status\": \"PLANNED\", \"progressPercent\": 10}")
                .andExpect(jsonPath("$.progressPercent").value(10));

        // COMPLETED straight from nothing also sets started_at.
        upsert(token, pyId, "{\"status\": \"COMPLETED\"}")
                .andExpect(jsonPath("$.startedAt").isNotEmpty())
                .andExpect(jsonPath("$.progressPercent").value(100));
    }

    @Test
    void invalidInputIsRejected() throws Exception {
        String token = register("ada@example.com");

        upsert(token, 999_999, "{\"status\": \"PLANNED\"}").andExpect(status().isNotFound());
        upsert(token, mlId, "{\"status\": \"IN_PROGRESS\", \"progressPercent\": 101}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.progressPercent").exists());
        upsert(token, mlId, "{}").andExpect(status().isBadRequest());
        upsert(token, mlId, "{\"status\": \"PLANNED\", \"notes\": \"" + "x".repeat(501) + "\"}")
                .andExpect(status().isBadRequest());
        assertThat(countProgressRows()).isZero();
    }

    // ---------------------------------------------------------------- isolation

    @Test
    void anotherUsersDataIsNeverVisible() throws Exception {
        String ada = register("ada@example.com");
        String bob = register("bob@example.com");
        upsert(ada, mlId, "{\"status\": \"IN_PROGRESS\", \"notes\": \"private\"}").andExpect(status().isOk());

        authed(get("/api/me/progress"), bob).andExpect(jsonPath("$.totalElements").value(0));
        authed(get("/api/me/progress/lookup").param("courseIds", String.valueOf(mlId)), bob)
                .andExpect(jsonPath("$.length()").value(0));
        authed(get("/api/me/progress/summary"), bob).andExpect(jsonPath("$.total").value(0));

        // Bob deleting "his" entry for the same course must not touch Ada's.
        authed(delete("/api/me/progress/{id}", mlId), bob).andExpect(status().isNoContent());
        String adaEntry = JsonPath.read(authed(get("/api/me/progress"), ada).andReturn().getResponse()
                .getContentAsString(), "$.content[0].entryId");
        authed(delete("/api/me/progress/entries/{id}", adaEntry), bob).andExpect(status().isNoContent());

        authed(get("/api/me/progress"), ada)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].notes").value("private"));
    }

    // ---------------------------------------------------------------- lookup, list, summary, delete

    @Test
    void lookupReturnsOnlyRequestedIds() throws Exception {
        String token = register("ada@example.com");
        upsert(token, mlId, "{\"status\": \"IN_PROGRESS\", \"progressPercent\": 25}");
        upsert(token, pyId, "{\"status\": \"COMPLETED\"}");

        String body = authed(get("/api/me/progress/lookup").param("courseIds", mlId + "," + cookId), token)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> result = JsonPath.read(body, "$");
        assertThat(result).containsOnlyKeys(String.valueOf(mlId));
        assertThat((String) JsonPath.read(body, "$['" + mlId + "'].status")).isEqualTo("IN_PROGRESS");
        assertThat((Integer) JsonPath.read(body, "$['" + mlId + "'].progressPercent")).isEqualTo(25);
    }

    @Test
    void lookupRejectsTooManyIds() throws Exception {
        String token = register("ada@example.com");
        String ids = String.join(",", java.util.stream.IntStream.rangeClosed(1, 101).mapToObj(String::valueOf).toList());

        authed(get("/api/me/progress/lookup").param("courseIds", ids), token).andExpect(status().isBadRequest());
        authed(get("/api/me/progress/lookup"), token).andExpect(status().isBadRequest());
    }

    @Test
    void listFiltersByStatusNewestFirstAndSummaryCounts() throws Exception {
        String token = register("ada@example.com");
        upsert(token, mlId, "{\"status\": \"PLANNED\"}");
        upsert(token, cookId, "{\"status\": \"COMPLETED\"}");
        upsert(token, pyId, "{\"status\": \"PLANNED\"}");

        authed(get("/api/me/progress"), token)
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].courseId").value(pyId));
        authed(get("/api/me/progress").param("status", "PLANNED"), token)
                .andExpect(jsonPath("$.totalElements").value(2));
        authed(get("/api/me/progress/summary"), token)
                .andExpect(jsonPath("$.planned").value(2))
                .andExpect(jsonPath("$.inProgress").value(0))
                .andExpect(jsonPath("$.completed").value(1))
                .andExpect(jsonPath("$.total").value(3));
    }

    @Test
    void deleteIsIdempotent() throws Exception {
        String token = register("ada@example.com");
        upsert(token, mlId, "{\"status\": \"PLANNED\"}");

        authed(delete("/api/me/progress/{id}", mlId), token).andExpect(status().isNoContent());
        authed(delete("/api/me/progress/{id}", mlId), token).andExpect(status().isNoContent());
        assertThat(countProgressRows()).isZero();
    }

    // ---------------------------------------------------------------- course removed by a sync

    @Test
    void progressSurvivesWhenTheCourseIsDeleted() throws Exception {
        String token = register("ada@example.com");
        upsert(token, cookId, "{\"status\": \"COMPLETED\", \"notes\": \"great pasta\"}");

        // Same as a complete Coursera sync in which c-cook no longer appears.
        Instant nextSync = now.plusSeconds(60);
        courseRepository.upsertAll(List.of(course(CourseSource.COURSERA, "c-ml", "Machine Learning")), nextSync);
        courseRepository.deleteNotSyncedSince(CourseSource.COURSERA, nextSync);

        String body = authed(get("/api/me/progress"), token)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].courseId").isEmpty())
                .andExpect(jsonPath("$.content[0].courseAvailable").value(false))
                .andExpect(jsonPath("$.content[0].course").isEmpty())
                .andExpect(jsonPath("$.content[0].courseTitle").value("Italian Cooking Basics"))
                .andExpect(jsonPath("$.content[0].notes").value("great pasta"))
                .andReturn().getResponse().getContentAsString();

        // The orphaned entry can still be removed by its entry id.
        String entryId = JsonPath.read(body, "$.content[0].entryId");
        authed(delete("/api/me/progress/entries/{id}", entryId), token).andExpect(status().isNoContent());
        assertThat(countProgressRows()).isZero();
    }

    // ---------------------------------------------------------------- public search untouched

    @Test
    void guestSearchStillWorksWithTheSameResponseShape() throws Exception {
        String token = register("ada@example.com");
        upsert(token, mlId, "{\"status\": \"IN_PROGRESS\"}");

        String guest = mockMvc.perform(get("/api/courses/search").param("q", "machine learning"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<Map<String, Object>>read(guest, "$").keySet()).containsExactlyInAnyOrder(
                "query", "fuzzyMatch", "content", "page", "size", "totalElements", "totalPages");
        assertThat(JsonPath.<Map<String, Object>>read(guest, "$.content[0]").keySet()).containsExactlyInAnyOrder(
                "id", "source", "sourceName", "title", "description", "url", "thumbnailUrl", "provider",
                "language", "level", "durationText", "free", "rating", "lastSyncedAt", "score");

        // A logged-in caller gets exactly the same body: no user data leaks into public search.
        String loggedIn = mockMvc.perform(get("/api/courses/search").param("q", "machine learning")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(loggedIn).isEqualTo(guest);
    }

    // ---------------------------------------------------------------- helpers

    private static ConnectorCourse course(CourseSource source, String id, String title) {
        return new ConnectorCourse(source, id, title, "About " + title, "https://example.com/" + id,
                null, "Provider", "en", null, null, source == CourseSource.YOUTUBE ? true : null, null);
    }

    private long idOf(String externalId) {
        Long id = jdbcTemplate.queryForObject("SELECT id FROM course WHERE external_id = ?", Long.class, externalId);
        return id == null ? -1 : id;
    }

    private long countProgressRows() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_course_progress", Long.class);
        return count == null ? 0 : count;
    }

    private ResultActions upsert(String token, long courseId, String body) throws Exception {
        return authed(put("/api/me/progress/{id}", courseId).contentType(MediaType.APPLICATION_JSON).content(body),
                token);
    }

    private ResultActions authed(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                                 String token) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token));
    }

    /** Registers a user (which also logs them in) and returns the access token. */
    private String register(String email) throws Exception {
        String response = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "%s", "password": "secret123", "displayName": "Ada"}
                        """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.accessToken");
    }
}
