package com.cineverse.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * In-process caches for slow-changing catalog data only. Seat availability, bookings and
 * payments are never cached: PostgreSQL stays the source of truth for inventory.
 */
@Configuration
public class CacheConfig {
    public static final String CITIES = "cities";
    public static final String GENRES = "genres";
    public static final String EVENT_CATEGORIES = "eventCategories";
    public static final String FOOD_MENU = "foodMenu";
    public static final String MOVIE_DETAIL = "movieDetail";
    public static final String CINEMAS_BY_CITY = "cinemasByCity";

    @Bean
    CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(CITIES, GENRES, EVENT_CATEGORIES, FOOD_MENU, MOVIE_DETAIL, CINEMAS_BY_CITY);
        manager.setCaffeine(Caffeine.newBuilder().maximumSize(2_000).expireAfterWrite(Duration.ofMinutes(10)));
        return manager;
    }
}
