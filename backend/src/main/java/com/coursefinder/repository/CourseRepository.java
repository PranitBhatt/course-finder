package com.coursefinder.repository;

import com.coursefinder.entity.Course;
import com.coursefinder.entity.CourseSource;
import com.coursefinder.repository.projection.SourceStatsRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Course persistence. Standard reads come from JPA; bulk upserts and ranked
 * full-text search come from the custom fragments {@link CourseUpsertRepository}
 * and {@link CourseSearchRepository}.
 */
public interface CourseRepository extends JpaRepository<Course, Long>,
        CourseUpsertRepository,
        CourseSearchRepository {

    long countBySource(CourseSource source);

    @Query("""
            SELECT new com.coursefinder.repository.projection.SourceStatsRow(
                       c.source, COUNT(c), MAX(c.lastSyncedAt))
            FROM Course c
            GROUP BY c.source
            """)
    List<SourceStatsRow> findSourceStats();

    /**
     * Removes courses that were not seen in the latest complete sync.
     *
     * @return number of deleted rows
     */
    @Transactional
    @Modifying
    @Query("DELETE FROM Course c WHERE c.source = :source AND c.lastSyncedAt < :syncStartedAt")
    int deleteNotSyncedSince(@Param("source") CourseSource source,
                             @Param("syncStartedAt") Instant syncStartedAt);
}
