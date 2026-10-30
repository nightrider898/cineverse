package com.cineverse.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Background jobs (lock cleanup, booking expiry, refunds, reminders). Tests turn this off and drive jobs directly. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "cineverse.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
