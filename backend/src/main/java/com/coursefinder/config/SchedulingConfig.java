package com.coursefinder.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables the nightly catalog sync ({@link com.coursefinder.scheduler.CourseraSyncScheduler})
 * and the daily refresh token cleanup ({@link com.coursefinder.scheduler.RefreshTokenCleanupJob}).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
