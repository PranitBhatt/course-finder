package com.coursefinder.repository;

import com.coursefinder.entity.ProgressStatus;
import com.coursefinder.entity.UserCourseProgress;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Every method takes the user id, so a query can never touch another user's rows by accident.
 */
public interface UserCourseProgressRepository extends JpaRepository<UserCourseProgress, Long> {

    Page<UserCourseProgress> findByUserId(Long userId, Pageable pageable);

    Page<UserCourseProgress> findByUserIdAndStatus(Long userId, ProgressStatus status, Pageable pageable);

    List<UserCourseProgress> findByUserIdAndCourseIdIn(Long userId, Collection<Long> courseIds);

    /**
     * Row lock (SELECT ... FOR UPDATE): two PUTs for the same course run one after the other,
     * so the second sees the first one's started_at/completed_at instead of overwriting them blindly.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<UserCourseProgress> findByUserIdAndCourseId(Long userId, Long courseId);

    /**
     * Creates the row with defaults (PLANNED, 0%) unless it already exists. ON CONFLICT makes two
     * simultaneous first PUTs safe: the second waits for the first, then does nothing, instead of
     * failing on the unique constraint. The caller then loads the row and applies the update.
     */
    @Modifying
    @Query(value = """
            INSERT INTO user_course_progress
                (user_id, course_id, course_title, course_url, course_source, created_at, updated_at)
            VALUES (:userId, :courseId, :title, :url, :source, :now, :now)
            ON CONFLICT (user_id, course_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") Long userId, @Param("courseId") Long courseId,
                       @Param("title") String title, @Param("url") String url,
                       @Param("source") String source, @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM UserCourseProgress p WHERE p.userId = :userId AND p.courseId = :courseId")
    int deleteByUserIdAndCourseId(@Param("userId") Long userId, @Param("courseId") Long courseId);

    @Modifying
    @Query("DELETE FROM UserCourseProgress p WHERE p.userId = :userId AND p.publicId = :publicId")
    int deleteByUserIdAndPublicId(@Param("userId") Long userId, @Param("publicId") UUID publicId);

    interface StatusCount {
        ProgressStatus getStatus();

        long getCount();
    }

    @Query("""
            SELECT p.status AS status, COUNT(p) AS count
            FROM UserCourseProgress p
            WHERE p.userId = :userId
            GROUP BY p.status
            """)
    List<StatusCount> countByStatus(@Param("userId") Long userId);
}
