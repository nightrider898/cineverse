package com.cineverse.controller;

import com.cineverse.common.ApiResponse;
import com.cineverse.common.PageResponse;
import com.cineverse.dto.EngagementDtos.*;
import com.cineverse.notification.NotificationService;
import com.cineverse.security.SecurityUtils;
import com.cineverse.service.FavoriteService;
import com.cineverse.service.ReviewService;
import com.cineverse.service.WatchlistService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Reviews, favorites, watchlist, notifications")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class EngagementController {
    private final ReviewService reviewService;
    private final FavoriteService favoriteService;
    private final WatchlistService watchlistService;
    private final NotificationService notificationService;

    @GetMapping("/reviews/movie/{movieId}")
    public ApiResponse<PageResponse<ReviewResponse>> reviews(@PathVariable Long movieId, @RequestParam(defaultValue = "0") int page,
                                                             @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(PageResponse.of(reviewService.forMovie(movieId, page, size)));
    }

    @GetMapping("/reviews/me")
    public ApiResponse<List<ReviewResponse>> myReviews() {
        return ApiResponse.ok(reviewService.mine(SecurityUtils.requireUser().id()));
    }

    @PostMapping("/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReviewResponse> review(@Valid @RequestBody ReviewRequest req) {
        return ApiResponse.ok(reviewService.create(SecurityUtils.requireUser().id(), req), "Thanks! Your review will appear after moderation.");
    }

    @PutMapping("/reviews/{id}")
    public ApiResponse<ReviewResponse> editReview(@PathVariable Long id, @Valid @RequestBody ReviewRequest req) {
        return ApiResponse.ok(reviewService.update(SecurityUtils.requireUser().id(), id, req));
    }

    @GetMapping("/favorites")
    public ApiResponse<List<FavoriteResponse>> favorites() {
        return ApiResponse.ok(favoriteService.list(SecurityUtils.requireUser().id()));
    }

    @PostMapping("/favorites")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<FavoriteResponse> addFavorite(@Valid @RequestBody FavoriteRequest req) {
        return ApiResponse.ok(favoriteService.add(SecurityUtils.requireUser().id(), req));
    }

    @DeleteMapping("/favorites/{id}")
    public ApiResponse<Void> removeFavorite(@PathVariable Long id) {
        favoriteService.remove(SecurityUtils.requireUser().id(), id);
        return ApiResponse.ok(null, "Removed from favorites");
    }

    @GetMapping("/watchlist")
    public ApiResponse<List<WatchlistResponse>> watchlist() {
        return ApiResponse.ok(watchlistService.list(SecurityUtils.requireUser().id()));
    }

    @PostMapping("/watchlist")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<WatchlistResponse> addWatchlist(@Valid @RequestBody WatchlistRequest req) {
        return ApiResponse.ok(watchlistService.add(SecurityUtils.requireUser().id(), req));
    }

    @DeleteMapping("/watchlist/{movieId}")
    public ApiResponse<Void> removeWatchlist(@PathVariable Long movieId) {
        watchlistService.remove(SecurityUtils.requireUser().id(), movieId);
        return ApiResponse.ok(null, "Removed from watchlist");
    }

    @GetMapping("/notifications")
    public ApiResponse<PageResponse<NotificationResponse>> notifications(@RequestParam(defaultValue = "0") int page,
                                                                        @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.of(notificationService.list(SecurityUtils.requireUser().id(), page, size)));
    }

    @GetMapping("/notifications/unread-count")
    public ApiResponse<UnreadCount> unread() {
        return ApiResponse.ok(new UnreadCount(notificationService.unread(SecurityUtils.requireUser().id())));
    }

    @PostMapping("/notifications/{id}/read")
    public ApiResponse<Void> read(@PathVariable Long id) {
        notificationService.markRead(SecurityUtils.requireUser().id(), id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/notifications/read-all")
    public ApiResponse<Void> readAll() {
        notificationService.markAllRead(SecurityUtils.requireUser().id());
        return ApiResponse.ok(null);
    }
}
