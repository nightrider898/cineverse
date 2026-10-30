package com.cineverse.unit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceDescriptionTest {
    @Test
    void describesCommonBrowsers() {
        assertThat(com.cineverse.security.UserAgents.describe(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0 Safari/537.36"))
                .isEqualTo("Chrome on Windows");
        assertThat(com.cineverse.security.UserAgents.describe(
                "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1"))
                .isEqualTo("Safari on iOS");
        assertThat(com.cineverse.security.UserAgents.describe(null)).isEqualTo("Unknown device");
    }
}
