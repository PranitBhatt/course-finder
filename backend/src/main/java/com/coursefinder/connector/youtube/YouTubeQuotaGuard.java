package com.coursefinder.connector.youtube;

import com.coursefinder.config.properties.YouTubeProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Keeps our YouTube API usage under a daily unit budget.
 * <p>
 * YouTube resets quotas at midnight Pacific Time, so the counter does too.
 * The counter is in memory: a restart resets it, which is acceptable for a personal project
 * (YouTube itself still enforces the real limit, and a 403 quotaExceeded marks us exhausted).
 */
@Component
public class YouTubeQuotaGuard {

    private static final ZoneId QUOTA_ZONE = ZoneId.of("America/Los_Angeles");

    private final int dailyLimit;
    private final Clock clock;

    private LocalDate currentDay;
    private int usedUnits;

    @Autowired
    public YouTubeQuotaGuard(YouTubeProperties properties) {
        this(properties.dailyQuotaUnits(), Clock.system(QUOTA_ZONE));
    }

    YouTubeQuotaGuard(int dailyLimit, Clock clock) {
        this.dailyLimit = dailyLimit;
        this.clock = clock;
        this.currentDay = today();
    }

    /** Reserves {@code units} if the budget allows it. */
    public synchronized boolean tryConsume(int units) {
        resetIfNewDay();
        if (usedUnits + units > dailyLimit) {
            return false;
        }
        usedUnits += units;
        return true;
    }

    /** Called when YouTube itself reports the quota is gone. */
    public synchronized void markExhausted() {
        resetIfNewDay();
        usedUnits = dailyLimit;
    }

    public synchronized int remainingUnits() {
        resetIfNewDay();
        return Math.max(0, dailyLimit - usedUnits);
    }

    private void resetIfNewDay() {
        LocalDate today = today();
        if (!today.equals(currentDay)) {
            currentDay = today;
            usedUnits = 0;
        }
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(QUOTA_ZONE));
    }
}
