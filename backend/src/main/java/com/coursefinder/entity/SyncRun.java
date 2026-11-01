package com.coursefinder.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * History of catalog syncs. Used for /api/sources and for debugging failed runs.
 */
@Entity
@Table(name = "sync_run")
public class SyncRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CourseSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SyncStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "items_upserted", nullable = false)
    private int itemsUpserted;

    @Column(name = "items_removed", nullable = false)
    private int itemsRemoved;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    protected SyncRun() {
        // for JPA
    }

    public static SyncRun start(CourseSource source) {
        SyncRun run = new SyncRun();
        run.source = source;
        run.status = SyncStatus.RUNNING;
        // Postgres stores microseconds. Truncating keeps "last_synced_at < startedAt" comparisons exact.
        run.startedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        return run;
    }

    public void markSuccess(int itemsUpserted, int itemsRemoved) {
        this.status = SyncStatus.SUCCESS;
        this.itemsUpserted = itemsUpserted;
        this.itemsRemoved = itemsRemoved;
        this.finishedAt = Instant.now();
    }

    public void markFailed(int itemsUpserted, String errorMessage) {
        this.status = SyncStatus.FAILED;
        this.itemsUpserted = itemsUpserted;
        this.errorMessage = errorMessage;
        this.finishedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public CourseSource getSource() {
        return source;
    }

    public SyncStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public int getItemsUpserted() {
        return itemsUpserted;
    }

    public int getItemsRemoved() {
        return itemsRemoved;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
