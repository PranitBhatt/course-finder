package com.coursefinder.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * Response of {@code GET /api/admin/stats}.
 *
 * @param searchesPerDay        last 30 days, oldest first, days with no searches included as 0
 * @param topZeroResultQueries  what people searched for and found nothing: content you're missing
 */
public record AdminStatsDto(
        long totalSearches,
        String timeZone,
        List<DailyCount> searchesPerDay,
        List<QueryCount> topQueries,
        List<QueryCount> topZeroResultQueries
) {

    public record DailyCount(LocalDate date, long searches) {
    }

    public record QueryCount(String query, long searches) {
    }
}
