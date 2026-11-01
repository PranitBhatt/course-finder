package com.coursefinder.repository;

import com.coursefinder.config.properties.SearchProperties;
import com.coursefinder.entity.CourseSource;
import com.coursefinder.repository.projection.CourseSearchRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.util.List;

/**
 * Search SQL lives here rather than in {@code @Query} strings so the ranking logic is easy
 * to read and filters can be appended only when present.
 * <p>
 * Indexes used (see V2 migration):
 * <ul>
 *   <li>{@code idx_course_search_vector} - GIN on the generated tsvector, for {@code @@}</li>
 *   <li>{@code idx_course_title_trgm} - GIN trigram on title, for {@code <%}</li>
 * </ul>
 */
public class CourseSearchRepositoryImpl implements CourseSearchRepository {

    private static final String SELECT_COLUMNS = """
            SELECT c.id, c.source, c.external_id, c.title, c.description, c.url, c.thumbnail_url,
                   c.provider, c.language, c.level, c.duration_text, c.is_free, c.rating,
                   c.last_synced_at,
            """;

    // websearch_to_tsquery accepts user input safely (never throws on syntax).
    private static final String FTS_FROM_WHERE = """
            FROM course c, websearch_to_tsquery('english', :query) AS tsq
            WHERE c.search_vector @@ tsq
            """;
    private static final String FTS_RANK = "ts_rank(c.search_vector, tsq) AS rank\n";

    // word_similarity compares the query with the best-matching part of the title,
    // so a short query can still match a long title. The <% operator uses the trigram index
    // and the session's pg_trgm.word_similarity_threshold, which we set per transaction.
    private static final String SET_FUZZY_THRESHOLD =
            "SELECT set_config('pg_trgm.word_similarity_threshold', :threshold, true)";
    private static final String FUZZY_FROM_WHERE = """
            FROM course c
            WHERE :query <% c.title
            """;
    private static final String FUZZY_RANK = "word_similarity(:query, c.title) AS rank\n";

    private static final String ORDER_AND_PAGE = """
            ORDER BY rank DESC, c.id
            LIMIT :limit OFFSET :offset
            """;

    private static final RowMapper<CourseSearchRow> ROW_MAPPER = (rs, rowNum) -> {
        Timestamp lastSyncedAt = rs.getTimestamp("last_synced_at");
        return new CourseSearchRow(
                rs.getLong("id"),
                CourseSource.valueOf(rs.getString("source")),
                rs.getString("external_id"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("url"),
                rs.getString("thumbnail_url"),
                rs.getString("provider"),
                rs.getString("language"),
                rs.getString("level"),
                rs.getString("duration_text"),
                rs.getObject("is_free", Boolean.class),
                rs.getBigDecimal("rating"),
                lastSyncedAt == null ? null : lastSyncedAt.toInstant(),
                rs.getDouble("rank")
        );
    };

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final TransactionTemplate readOnlyTransaction;
    private final String fuzzyThreshold;

    public CourseSearchRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate,
                                      PlatformTransactionManager transactionManager,
                                      SearchProperties searchProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.readOnlyTransaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction.setReadOnly(true);
        this.fuzzyThreshold = String.valueOf(searchProperties.fuzzyThreshold());
    }

    @Override
    public Page<CourseSearchRow> fullTextSearch(String query, CourseSource source, Boolean free, Pageable pageable) {
        return search(FTS_FROM_WHERE, FTS_RANK, query, source, free, pageable);
    }

    @Override
    public Page<CourseSearchRow> fuzzyTitleSearch(String query, CourseSource source, Boolean free, Pageable pageable) {
        // One transaction = one connection, so the threshold applies to both queries and is reset after.
        return readOnlyTransaction.execute(status -> {
            jdbcTemplate.queryForObject(SET_FUZZY_THRESHOLD,
                    new MapSqlParameterSource("threshold", fuzzyThreshold), String.class);
            return search(FUZZY_FROM_WHERE, FUZZY_RANK, query, source, free, pageable);
        });
    }

    private Page<CourseSearchRow> search(String fromWhere, String rankExpression,
                                         String query, CourseSource source, Boolean free,
                                         Pageable pageable) {
        MapSqlParameterSource params = new MapSqlParameterSource("query", query);
        String filters = buildFilters(source, free, params);

        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) " + fromWhere + filters, params, Long.class);
        if (total == null || total == 0) {
            return Page.empty(pageable);
        }

        params.addValue("limit", pageable.getPageSize());
        params.addValue("offset", pageable.getOffset());
        String sql = SELECT_COLUMNS + rankExpression + fromWhere + filters + ORDER_AND_PAGE;

        List<CourseSearchRow> rows = jdbcTemplate.query(sql, params, ROW_MAPPER);
        return new PageImpl<>(rows, pageable, total);
    }

    private static String buildFilters(CourseSource source, Boolean free, MapSqlParameterSource params) {
        StringBuilder filters = new StringBuilder();
        if (source != null) {
            filters.append("AND c.source = :source\n");
            params.addValue("source", source.name());
        }
        if (free != null) {
            filters.append("AND c.is_free = :free\n");
            params.addValue("free", free);
        }
        return filters.toString();
    }
}
