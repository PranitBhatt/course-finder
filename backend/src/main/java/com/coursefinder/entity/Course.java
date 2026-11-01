package com.coursefinder.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A course from any platform. Rows are written by the upsert in
 * {@link com.coursefinder.repository.CourseUpsertRepositoryImpl}; this entity is mainly for reads.
 * The generated {@code search_vector} column is intentionally not mapped.
 */
@Entity
@Table(name = "course")
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CourseSource source;

    @Column(name = "external_id", nullable = false)
    private String externalId;

    @Column(nullable = false, columnDefinition = "text")
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false, columnDefinition = "text")
    private String url;

    @Column(name = "thumbnail_url", columnDefinition = "text")
    private String thumbnailUrl;

    @Column(columnDefinition = "text")
    private String provider;

    @Column(length = 16)
    private String language;

    @Column(length = 32)
    private String level;

    @Column(name = "duration_text", length = 128)
    private String durationText;

    @Column(name = "is_free")
    private Boolean free;

    @Column(precision = 3, scale = 2)
    private BigDecimal rating;

    @Column(name = "last_synced_at", nullable = false)
    private Instant lastSyncedAt;

    protected Course() {
        // for JPA
    }

    public Course(CourseSource source, String externalId, String title, String url) {
        this.source = source;
        this.externalId = externalId;
        this.title = title;
        this.url = url;
        this.lastSyncedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public CourseSource getSource() {
        return source;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getDurationText() {
        return durationText;
    }

    public void setDurationText(String durationText) {
        this.durationText = durationText;
    }

    public Boolean getFree() {
        return free;
    }

    public void setFree(Boolean free) {
        this.free = free;
    }

    public BigDecimal getRating() {
        return rating;
    }

    public void setRating(BigDecimal rating) {
        this.rating = rating;
    }

    public Instant getLastSyncedAt() {
        return lastSyncedAt;
    }

    public void setLastSyncedAt(Instant lastSyncedAt) {
        this.lastSyncedAt = lastSyncedAt;
    }
}
