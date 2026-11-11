package com.coursefinder.dto.request;

import com.coursefinder.entity.ProgressStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PUT /api/me/progress/{courseId}}.
 *
 * @param progressPercent optional; left out = keep the current value (COMPLETED always means 100,
 *                        PLANNED without a value means 0)
 * @param notes           optional; left out = keep the current notes, "" = remove them
 */
public record ProgressUpdateRequest(
        @NotNull(message = "must not be null")
        ProgressStatus status,

        @Min(value = 0, message = "must be 0 or greater")
        @Max(value = 100, message = "must be at most 100")
        Integer progressPercent,

        @Size(max = 500, message = "must be at most 500 characters")
        String notes
) {
}
