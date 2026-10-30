package com.cineverse;

import com.cineverse.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
@EnableAsync
@EnableCaching
public class CineVerseApplication {
    public static void main(String[] args) {
        SpringApplication.run(CineVerseApplication.class, args);
    }
}
