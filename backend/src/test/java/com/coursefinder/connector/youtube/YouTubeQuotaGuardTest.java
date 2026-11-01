package com.coursefinder.connector.youtube;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class YouTubeQuotaGuardTest {

    /** A clock we can move forward. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void consumesUntilLimitThenRefuses() {
        YouTubeQuotaGuard guard = new YouTubeQuotaGuard(250, Clock.systemUTC());

        assertThat(guard.tryConsume(100)).isTrue();
        assertThat(guard.tryConsume(100)).isTrue();
        assertThat(guard.tryConsume(100)).isFalse();
        assertThat(guard.remainingUnits()).isEqualTo(50);
    }

    @Test
    void resetsOnNewQuotaDay() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-10T12:00:00Z"));
        YouTubeQuotaGuard guard = new YouTubeQuotaGuard(100, clock);
        guard.markExhausted();
        assertThat(guard.tryConsume(1)).isFalse();

        clock.advance(Duration.ofDays(1));

        assertThat(guard.tryConsume(100)).isTrue();
    }
}
