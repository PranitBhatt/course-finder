package com.coursefinder.dto.response;

/** Counts per status for the summary row on "My Learning"; total = all three together. */
public record ProgressSummaryDto(long planned, long inProgress, long completed, long total) {
}
