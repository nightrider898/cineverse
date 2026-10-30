package com.cineverse.support;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestConfig {
    @Bean
    @Primary
    public MutableClock testClock() {
        return new MutableClock();
    }

    /** Every test run starts from a freshly migrated schema. */
    @Bean
    public FlywayMigrationStrategy cleanMigrate() {
        return (Flyway flyway) -> {
            flyway.clean();
            flyway.migrate();
        };
    }
}
