package com.coursefinder.dto.response;

import com.coursefinder.entity.ProgressStatus;

/** The small per-course status that search result cards show. */
public record ProgressStatusDto(ProgressStatus status, int progressPercent) {
}
