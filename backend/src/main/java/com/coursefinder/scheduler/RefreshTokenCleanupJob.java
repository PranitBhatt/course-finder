package com.coursefinder.scheduler;

import com.coursefinder.service.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deletes expired refresh tokens once a day, so the table doesn't grow forever.
 * <p>
 * On Render's free tier the app sleeps when nobody uses it, and a sleeping app runs no cron jobs
 * (missed runs are not made up later). That's acceptable here: this is only housekeeping.
 * Expired tokens are already rejected by the expiry check in {@link SessionService#refresh},
 * so a skipped cleanup only means a few extra rows until the next day the app is awake.
 */
@Component
public class RefreshTokenCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupJob.class);

    private final SessionService sessionService;

    public RefreshTokenCleanupJob(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Scheduled(cron = "${app.refresh-token.cleanup-cron:0 30 4 * * *}")
    public void deleteExpiredTokens() {
        int deleted = sessionService.deleteExpiredRefreshTokens();
        log.info("Deleted {} expired refresh tokens", deleted);
    }
}
