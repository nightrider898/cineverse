package com.cineverse.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

import java.time.Clock;

@Configuration
public class AsyncConfig {
    /** Virtual-thread executor for fire-and-forget work such as email delivery. */
    @Bean(name = "taskExecutor")
    TaskExecutor taskExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("cv-async-");
        executor.setVirtualThreads(true);
        return executor;
    }

    /** Injected everywhere time matters so tests can control expiry and cancellation windows. */
    @Bean
    @ConditionalOnMissingBean
    Clock clock() {
        return Clock.systemUTC();
    }
}
