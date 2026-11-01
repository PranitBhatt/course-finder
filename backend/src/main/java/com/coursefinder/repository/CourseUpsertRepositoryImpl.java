package com.coursefinder.repository;

import com.coursefinder.connector.ConnectorCourse;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Batched {@code INSERT ... ON CONFLICT DO UPDATE}. Much faster than JPA {@code save()}
 * for tens of thousands of rows, and avoids a select-before-insert per course.
 * <p>
 * Spring Data picks this up automatically because of the {@code Impl} suffix.
 */
public class CourseUpsertRepositoryImpl implements CourseUpsertRepository {

    private static final String UPSERT_SQL = """
            INSERT INTO course (source, external_id, title, description, url, thumbnail_url,
                                provider, language, level, duration_text, is_free, rating, last_synced_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (source, external_id) DO UPDATE SET
                title          = EXCLUDED.title,
                description    = EXCLUDED.description,
                url            = EXCLUDED.url,
                thumbnail_url  = EXCLUDED.thumbnail_url,
                provider       = EXCLUDED.provider,
                language       = EXCLUDED.language,
                level          = EXCLUDED.level,
                duration_text  = EXCLUDED.duration_text,
                is_free        = EXCLUDED.is_free,
                rating         = EXCLUDED.rating,
                last_synced_at = EXCLUDED.last_synced_at
            """;

    // Must match the VARCHAR sizes in V2__create_course_table.sql.
    private static final int MAX_EXTERNAL_ID = 255;
    private static final int MAX_LANGUAGE = 16;
    private static final int MAX_LEVEL = 32;
    private static final int MAX_DURATION_TEXT = 128;

    private final JdbcTemplate jdbcTemplate;

    public CourseUpsertRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public int upsertAll(List<ConnectorCourse> courses, Instant syncedAt) {
        if (courses.isEmpty()) {
            return 0;
        }
        OffsetDateTime syncedAtUtc = OffsetDateTime.ofInstant(syncedAt, ZoneOffset.UTC);

        jdbcTemplate.batchUpdate(UPSERT_SQL, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                ConnectorCourse course = courses.get(i);
                ps.setString(1, course.source().name());
                ps.setString(2, truncate(course.externalId(), MAX_EXTERNAL_ID));
                ps.setString(3, course.title());
                ps.setString(4, course.description());
                ps.setString(5, course.url());
                ps.setString(6, course.thumbnailUrl());
                ps.setString(7, course.provider());
                ps.setString(8, truncate(course.language(), MAX_LANGUAGE));
                ps.setString(9, truncate(course.level(), MAX_LEVEL));
                ps.setString(10, truncate(course.durationText(), MAX_DURATION_TEXT));
                if (course.free() == null) {
                    ps.setNull(11, Types.BOOLEAN);
                } else {
                    ps.setBoolean(11, course.free());
                }
                ps.setBigDecimal(12, course.rating());
                ps.setObject(13, syncedAtUtc);
            }

            @Override
            public int getBatchSize() {
                return courses.size();
            }
        });
        return courses.size();
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
