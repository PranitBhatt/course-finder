package com.coursefinder.service;

import com.coursefinder.dto.request.ProgressUpdateRequest;
import com.coursefinder.entity.ProgressStatus;
import com.coursefinder.entity.UserCourseProgress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** The status rules on their own, without a database (the integration test covers them over HTTP). */
class ProgressStatusRulesTest {

    private static final Instant T1 = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-01-02T10:00:00Z");

    private UserCourseProgress progress;

    @BeforeEach
    void newRow() {
        // Rows start like the INSERT in insertIfAbsent leaves them: PLANNED, 0%.
        progress = new UserCourseProgress() {
        };
        progress.setStatus(ProgressStatus.PLANNED);
        progress.setProgressPercent(0);
    }

    @Test
    void inProgressSetsStartedAtOnce() {
        apply(ProgressStatus.IN_PROGRESS, 20, null, T1);
        apply(ProgressStatus.IN_PROGRESS, 40, null, T2);

        assertThat(progress.getStartedAt()).isEqualTo(T1);
        assertThat(progress.getProgressPercent()).isEqualTo(40);
    }

    @Test
    void inProgressWithoutPercentKeepsTheCurrentOne() {
        apply(ProgressStatus.IN_PROGRESS, 35, null, T1);
        apply(ProgressStatus.IN_PROGRESS, null, null, T2);

        assertThat(progress.getProgressPercent()).isEqualTo(35);
    }

    @Test
    void completedForces100AndSetsDates() {
        apply(ProgressStatus.COMPLETED, 10, null, T1);

        assertThat(progress.getProgressPercent()).isEqualTo(100);
        assertThat(progress.getCompletedAt()).isEqualTo(T1);
        assertThat(progress.getStartedAt()).isEqualTo(T1);
    }

    @Test
    void completedAgainKeepsTheOriginalDate() {
        apply(ProgressStatus.COMPLETED, null, null, T1);
        apply(ProgressStatus.COMPLETED, null, null, T2);

        assertThat(progress.getCompletedAt()).isEqualTo(T1);
    }

    @Test
    void leavingCompletedClearsCompletedAtButKeepsStartedAt() {
        apply(ProgressStatus.COMPLETED, null, null, T1);
        apply(ProgressStatus.IN_PROGRESS, 70, null, T2);

        assertThat(progress.getCompletedAt()).isNull();
        assertThat(progress.getStartedAt()).isEqualTo(T1);
        assertThat(progress.getProgressPercent()).isEqualTo(70);
    }

    @Test
    void plannedResetsPercentUnlessGiven() {
        apply(ProgressStatus.IN_PROGRESS, 50, null, T1);
        apply(ProgressStatus.PLANNED, null, null, T2);
        assertThat(progress.getProgressPercent()).isZero();

        apply(ProgressStatus.PLANNED, 15, null, T2);
        assertThat(progress.getProgressPercent()).isEqualTo(15);
    }

    @Test
    void notesAreKeptWhenLeftOutAndRemovedWhenBlank() {
        apply(ProgressStatus.PLANNED, null, "  read chapter 1  ", T1);
        assertThat(progress.getNotes()).isEqualTo("read chapter 1");

        apply(ProgressStatus.IN_PROGRESS, null, null, T2);
        assertThat(progress.getNotes()).isEqualTo("read chapter 1");

        apply(ProgressStatus.IN_PROGRESS, null, "   ", T2);
        assertThat(progress.getNotes()).isNull();
    }

    private void apply(ProgressStatus status, Integer percent, String notes, Instant now) {
        ProgressService.applyUpdate(progress, new ProgressUpdateRequest(status, percent, notes), now);
    }
}
