package com.cineverse.dto;

import com.cineverse.entity.enums.FavoriteType;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.time.LocalDate;

public final class EngagementDtos {
    private EngagementDtos() {}

    public record ReviewRequest(@NotNull Long movieId, @Min(1) @Max(5) int rating,
                                @Size(max = 120) String title, @Size(max = 2000) String body) {}

    public record ReviewResponse(Long id, Long movieId, String movieTitle, String userName, int rating, String title,
                                 String body, String status, Instant createdAt, boolean verifiedBooking) {}

    public record FavoriteRequest(@NotNull FavoriteType targetType, @NotNull Long targetId) {}

    public record FavoriteResponse(Long id, String targetType, Long targetId, String title, String subtitle,
                                   String imageUrl, String link, Instant createdAt) {}

    public record WatchlistRequest(@NotNull Long movieId, Boolean remindMe) {}

    public record WatchlistResponse(Long id, CatalogDtos.MovieResponse movie, boolean remindMe, LocalDate releaseDate, Instant createdAt) {}

    public record NotificationResponse(Long id, String type, String title, String body, String link, boolean read, Instant createdAt) {}

    public record UnreadCount(long unread) {}
}
