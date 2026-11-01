package com.coursefinder.repository;

import com.coursefinder.entity.CourseSource;
import com.coursefinder.entity.SyncRun;
import com.coursefinder.entity.SyncStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SyncRunRepository extends JpaRepository<SyncRun, Long> {

    Optional<SyncRun> findFirstBySourceOrderByStartedAtDesc(CourseSource source);

    Optional<SyncRun> findFirstBySourceAndStatusOrderByStartedAtDesc(CourseSource source, SyncStatus status);
}
