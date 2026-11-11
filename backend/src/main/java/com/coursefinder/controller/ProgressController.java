package com.coursefinder.controller;

import com.coursefinder.dto.request.ProgressUpdateRequest;
import com.coursefinder.dto.response.ProgressItemDto;
import com.coursefinder.dto.response.ProgressPageResponse;
import com.coursefinder.dto.response.ProgressStatusDto;
import com.coursefinder.dto.response.ProgressSummaryDto;
import com.coursefinder.entity.ProgressStatus;
import com.coursefinder.service.ProgressService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The logged-in user's course progress ("My Learning").
 * <p>
 * Not listed in SecurityConfig's public endpoints, so every route needs a valid Bearer token (401
 * otherwise, also when auth is switched off). The user always comes from the token's "sub", never
 * from the request. The public search endpoints stay untouched: they carry no user data, so they
 * stay the same for everyone.
 */
@RestController
@RequestMapping("/api/me/progress")
public class ProgressController {

    static final int MAX_LOOKUP_IDS = 100;

    private final ProgressService progressService;

    public ProgressController(ProgressService progressService) {
        this.progressService = progressService;
    }

    /** {@code GET /api/me/progress?status=IN_PROGRESS&page=0&size=20} - newest change first. */
    @GetMapping
    public ProgressPageResponse list(@AuthenticationPrincipal Jwt jwt,
                                     @RequestParam(required = false) ProgressStatus status,
                                     @RequestParam(defaultValue = "0")
                                     @Min(value = 0, message = "must be 0 or greater") int page,
                                     @RequestParam(defaultValue = "20")
                                     @Min(value = 1, message = "must be at least 1")
                                     @Max(value = 100, message = "must be at most 100") int size) {
        return progressService.list(userId(jwt), status, page, size);
    }

    /**
     * {@code GET /api/me/progress/lookup?courseIds=1,2,3} - {@code {"1": {"status": ..., "progressPercent": ...}}}
     * for tracked courses among those ids. One call per search results page instead of one per card.
     */
    @GetMapping("/lookup")
    public Map<Long, ProgressStatusDto> lookup(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam
            @NotEmpty(message = "must not be empty")
            @Size(max = MAX_LOOKUP_IDS, message = "must contain at most " + MAX_LOOKUP_IDS + " ids")
            List<@Positive(message = "must be positive numbers") Long> courseIds) {
        return progressService.lookup(userId(jwt), new LinkedHashSet<>(courseIds));
    }

    /** {@code GET /api/me/progress/summary} */
    @GetMapping("/summary")
    public ProgressSummaryDto summary(@AuthenticationPrincipal Jwt jwt) {
        return progressService.summary(userId(jwt));
    }

    /**
     * {@code PUT /api/me/progress/42} - create or update (idempotent, safe to retry). Always 200
     * with the stored entry; 404 if the course doesn't exist.
     */
    @PutMapping("/{courseId}")
    public ProgressItemDto upsert(@AuthenticationPrincipal Jwt jwt,
                                  @PathVariable @Positive(message = "must be a positive number") long courseId,
                                  @Valid @RequestBody ProgressUpdateRequest request) {
        return progressService.upsert(userId(jwt), courseId, request);
    }

    /** {@code DELETE /api/me/progress/42} - always 204, also when nothing was tracked. */
    @DeleteMapping("/{courseId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt,
                                       @PathVariable @Positive(message = "must be a positive number") long courseId) {
        progressService.delete(userId(jwt), courseId);
        return ResponseEntity.noContent().build();
    }

    /**
     * {@code DELETE /api/me/progress/entries/{entryId}} - always 204. Needed for entries whose course
     * was removed by a sync: they have no course id any more, so the route above can't reach them.
     */
    @DeleteMapping("/entries/{entryId}")
    public ResponseEntity<Void> deleteEntry(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID entryId) {
        progressService.deleteEntry(userId(jwt), entryId);
        return ResponseEntity.noContent().build();
    }

    // "sub" is the public id; the JwtDecoder already made sure it's a valid UUID (see MeController).
    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
