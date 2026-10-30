package com.cineverse.admin;

import com.cineverse.admin.AdminDtos.*;
import com.cineverse.common.ApiResponse;
import com.cineverse.common.PageResponse;
import com.cineverse.dto.CatalogDtos.VenueSummary;
import com.cineverse.exception.ApiException;
import com.cineverse.exception.ErrorCode;
import com.cineverse.media.MediaSyncService;
import com.cineverse.media.TmdbClient;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Catalog administration. URL-level guard: ADMIN or PARTNER; finer checks in {@link AdminAccess}. */
@Tag(name = "Admin: catalog")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminCatalogController {
    private final AdminCatalogService service;
    private final MediaSyncService mediaSync;
    private final AdminAccess access;

    /** Re-fetches poster, backdrop, trailer and missing details for one movie from TMDB / YouTube. */
    @PostMapping("/movies/{id}/sync-media")
    public ApiResponse<MediaSyncService.SyncResult> syncMovieMedia(@PathVariable Long id) {
        access.requireAdmin();
        service.movie(id); // 404 for unknown ids
        try {
            MediaSyncService.SyncResult r = mediaSync.syncMovie(id);
            return ApiResponse.ok(r, r.matched() ? "Media updated" : "No confident TMDB match. Set the poster and trailer manually.");
        } catch (TmdbClient.MediaSyncException e) {
            throw new ApiException(ErrorCode.MEDIA_PROVIDER_UNAVAILABLE, e.getMessage());
        }
    }

    /** Starts a background sync of every movie (force=true also refreshes movies synced recently). */
    @PostMapping("/media/sync")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<Map<String, Object>> syncAllMedia(@RequestParam(defaultValue = "false") boolean force) {
        access.requireAdmin();
        if (mediaSync.isRunning()) return ApiResponse.ok(Map.of("running", true), "A media sync is already running");
        Thread.ofVirtual().name("media-sync-admin").start(() -> mediaSync.syncPending(force));
        return ApiResponse.ok(Map.of("running", true), "Media sync started. Posters and trailers will appear over the next few minutes.");
    }

    @GetMapping("/media/status")
    public ApiResponse<Map<String, Object>> mediaStatus() {
        access.requireAdmin();
        return ApiResponse.ok(service.mediaStatus(mediaSync.isRunning()));
    }

    @GetMapping("/movies")
    public ApiResponse<PageResponse<AdminMovieResponse>> movies(@RequestParam(required = false) String q,
                                                                @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(PageResponse.of(service.movies(q, page, size)));
    }

    @GetMapping("/movies/{id}")
    public ApiResponse<AdminMovieResponse> movie(@PathVariable Long id) {
        return ApiResponse.ok(service.movie(id));
    }

    @PostMapping("/movies")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AdminMovieResponse> createMovie(@Valid @RequestBody MovieUpsertRequest req) {
        return ApiResponse.ok(service.createMovie(req), "Movie created");
    }

    @PutMapping("/movies/{id}")
    public ApiResponse<AdminMovieResponse> updateMovie(@PathVariable Long id, @Valid @RequestBody MovieUpsertRequest req) {
        return ApiResponse.ok(service.updateMovie(id, req), "Movie updated");
    }

    @DeleteMapping("/movies/{id}")
    public ApiResponse<Void> deleteMovie(@PathVariable Long id) {
        service.deactivateMovie(id);
        return ApiResponse.ok(null, "Movie deactivated");
    }

    @GetMapping("/cinemas")
    public ApiResponse<List<AdminCinemaResponse>> cinemas(@RequestParam(required = false) Long cityId) {
        return ApiResponse.ok(service.cinemas(cityId));
    }

    @PostMapping("/cinemas")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AdminCinemaResponse> createCinema(@Valid @RequestBody CinemaUpsertRequest req) {
        return ApiResponse.ok(service.createCinema(req), "Cinema created");
    }

    @PutMapping("/cinemas/{id}")
    public ApiResponse<AdminCinemaResponse> updateCinema(@PathVariable Long id, @Valid @RequestBody CinemaUpsertRequest req) {
        return ApiResponse.ok(service.updateCinema(id, req), "Cinema updated");
    }

    @DeleteMapping("/cinemas/{id}")
    public ApiResponse<Void> deleteCinema(@PathVariable Long id) {
        service.deactivateCinema(id);
        return ApiResponse.ok(null, "Cinema deactivated");
    }

    @GetMapping("/venues")
    public ApiResponse<List<VenueSummary>> venues() {
        return ApiResponse.ok(service.venues());
    }

    @PostMapping("/venues")
    public ApiResponse<Map<String, Long>> createVenue(@Valid @RequestBody VenueUpsertRequest req) {
        return ApiResponse.ok(Map.of("id", service.upsertVenue(null, req)), "Venue created");
    }

    @PutMapping("/venues/{id}")
    public ApiResponse<Map<String, Long>> updateVenue(@PathVariable Long id, @Valid @RequestBody VenueUpsertRequest req) {
        return ApiResponse.ok(Map.of("id", service.upsertVenue(id, req)), "Venue updated");
    }

    @GetMapping("/seat-types")
    public ApiResponse<List<SeatTypeResponse>> seatTypes() {
        return ApiResponse.ok(service.seatTypes());
    }

    @GetMapping("/screens")
    public ApiResponse<List<AdminScreenResponse>> screens(@RequestParam Long cinemaId) {
        return ApiResponse.ok(service.screens(cinemaId));
    }

    @GetMapping("/screens/{id}")
    public ApiResponse<AdminScreenResponse> screen(@PathVariable Long id) {
        return ApiResponse.ok(service.screen(id));
    }

    @PostMapping("/screens")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AdminScreenResponse> createScreen(@Valid @RequestBody ScreenUpsertRequest req) {
        return ApiResponse.ok(service.createScreen(req), "Screen created");
    }

    @PutMapping("/screens/{id}")
    public ApiResponse<AdminScreenResponse> updateScreen(@PathVariable Long id, @Valid @RequestBody ScreenUpsertRequest req) {
        return ApiResponse.ok(service.updateScreen(id, req), "Screen updated");
    }

    @PutMapping("/screens/{id}/layout")
    public ApiResponse<AdminScreenResponse> layout(@PathVariable Long id, @Valid @RequestBody SeatLayoutRequest req) {
        return ApiResponse.ok(service.saveLayout(id, req), "Seat layout saved");
    }

    @GetMapping("/shows")
    public ApiResponse<PageResponse<AdminShowResponse>> shows(@RequestParam(required = false) Long cinemaId, @RequestParam(required = false) Long movieId,
                                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                              @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "100") int size) {
        return ApiResponse.ok(PageResponse.of(service.shows(cinemaId, movieId, date, page, size)));
    }

    @PostMapping("/shows")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AdminShowResponse> createShow(@Valid @RequestBody ShowCreateRequest req) {
        return ApiResponse.ok(service.createShow(req), "Show scheduled");
    }

    @PatchMapping("/shows/{id}")
    public ApiResponse<AdminShowResponse> updateShow(@PathVariable Long id, @Valid @RequestBody ShowUpdateRequest req) {
        return ApiResponse.ok(service.updateShow(id, req), "Show updated");
    }

    @GetMapping("/events")
    public ApiResponse<PageResponse<AdminEventResponse>> events(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(PageResponse.of(service.events(page, size)));
    }

    @GetMapping("/events/{id}")
    public ApiResponse<AdminEventResponse> event(@PathVariable Long id) {
        return ApiResponse.ok(service.event(id));
    }

    @PostMapping("/events")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AdminEventResponse> createEvent(@Valid @RequestBody EventUpsertRequest req) {
        return ApiResponse.ok(service.createEvent(req), "Event created");
    }

    @PutMapping("/events/{id}")
    public ApiResponse<AdminEventResponse> updateEvent(@PathVariable Long id, @Valid @RequestBody EventUpsertRequest req) {
        return ApiResponse.ok(service.updateEvent(id, req), "Event updated");
    }
}
