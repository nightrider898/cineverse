package com.cineverse.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** Full Spring context against a real PostgreSQL database (cineverse_test). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestConfig.class)
public abstract class IntegrationTest {
    @Autowired protected MutableClock clock;
    @Autowired protected Fixtures fixtures;

    @BeforeEach
    void resetClock() {
        clock.reset();
    }

    @AfterEach
    void restoreClock() {
        clock.reset();
    }
}
