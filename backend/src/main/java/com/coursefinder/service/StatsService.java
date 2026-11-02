package com.coursefinder.service;

import com.coursefinder.config.properties.StatsProperties;
import com.coursefinder.dto.response.AdminStatsDto;
import com.coursefinder.dto.response.AdminStatsDto.DailyCount;
import com.coursefinder.dto.response.AdminStatsDto.QueryCount;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Aggregates {@code search_log} for the admin stats endpoint. */
@Service
public class StatsService {

    static final int DAYS = 30;
    static final int TOP_LIMIT = 20;

    private static final String TOTAL_SQL = "SELECT COUNT(*) FROM search_log";

    // Every one of the last 30 days, including days without searches.
    private static final String PER_DAY_SQL = """
            WITH days AS (
                SELECT CAST(d AS date) AS day
                FROM generate_series(CAST(now() AT TIME ZONE ? AS date) - ?,
                                     CAST(now() AT TIME ZONE ? AS date),
                                     INTERVAL '1 day') AS d
            )
            SELECT days.day, COUNT(s.id) AS searches
            FROM days
            LEFT JOIN search_log s ON CAST(s.created_at AT TIME ZONE ? AS date) = days.day
            GROUP BY days.day
            ORDER BY days.day
            """;

    private static final String TOP_QUERIES_SQL = """
            SELECT query, COUNT(*) AS searches
            FROM search_log
            %s
            GROUP BY query
            ORDER BY searches DESC, query
            LIMIT ?
            """;

    private static final RowMapper<QueryCount> QUERY_COUNT_MAPPER =
            (rs, rowNum) -> new QueryCount(rs.getString("query"), rs.getLong("searches"));

    private final JdbcTemplate jdbcTemplate;
    private final String zone;

    public StatsService(JdbcTemplate jdbcTemplate, StatsProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.zone = properties.timeZone().getId();
    }

    @Transactional(readOnly = true)
    public AdminStatsDto getStats() {
        Long total = jdbcTemplate.queryForObject(TOTAL_SQL, Long.class);

        List<DailyCount> perDay = jdbcTemplate.query(PER_DAY_SQL,
                (rs, rowNum) -> new DailyCount(rs.getDate("day").toLocalDate(), rs.getLong("searches")),
                zone, DAYS - 1, zone, zone);

        List<QueryCount> topQueries = jdbcTemplate.query(
                TOP_QUERIES_SQL.formatted(""), QUERY_COUNT_MAPPER, TOP_LIMIT);
        List<QueryCount> zeroResultQueries = jdbcTemplate.query(
                TOP_QUERIES_SQL.formatted("WHERE result_count = 0"), QUERY_COUNT_MAPPER, TOP_LIMIT);

        return new AdminStatsDto(total == null ? 0 : total, zone, perDay, topQueries, zeroResultQueries);
    }
}
