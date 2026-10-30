package com.cineverse.controller;

import com.cineverse.common.ApiResponse;
import com.cineverse.common.PageResponse;
import com.cineverse.dto.CatalogDtos.*;
import com.cineverse.security.SecurityUtils;
import com.cineverse.service.MovieService;
import com.cineverse.service.ShowService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Tag(name = "Movies")
@RestController
@RequestMapping("/api/v1/movies")
@RequiredArgsConstructor
public class MovieController {
    private final MovieService movieService;
    private final ShowService showService;

    /** e.g. GET /movies?city=hyderabad&language=Telugu&genre=Action&sort=rating,desc&page=0&size=20 */
    @GetMapping
    public ApiResponse<PageResponse<MovieResponse>> list(@RequestParam(required = false) String city,
                                                         @RequestParam(required = false) String language,
                                                         @RequestParam(required = false) String genre,
                                                         @RequestParam(required = false) String format,
                                                         @RequestParam(required = false) String status,
                                                         @RequestParam(required = false) BigDecimal minRating,
                                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                         @RequestParam(required = false) Long cinemaId,
                                                         @RequestParam(required = false) String q,
                                                         @RequestParam(required = false) String sort,
                                                         @RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "20") int size) {
        var filter = new MovieService.MovieFilter(city, language, genre, format, status, minRating, date, cinemaId, q);
        return ApiResponse.ok(PageResponse.of(movieService.list(filter, page, size, sort)));
    }

    @GetMapping("/home")
    public ApiResponse<MovieHomeResponse> home(@RequestParam(required = false) String city) {
        return ApiResponse.ok(movieService.home(city, SecurityUtils.currentUserIdOrNull()));
    }

    @GetMapping("/{id}")
    public ApiResponse<MovieDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.ok(movieService.detail(id));
    }

    @GetMapping("/slug/{slug}")
    public ApiResponse<MovieDetailResponse> bySlug(@PathVariable String slug) {
        return ApiResponse.ok(movieService.detailBySlug(slug));
    }

    @GetMapping("/{id}/similar")
    public ApiResponse<List<MovieResponse>> similar(@PathVariable Long id) {
        return ApiResponse.ok(movieService.similar(id));
    }

    @GetMapping("/{id}/showtimes")
    public ApiResponse<MovieShowtimesResponse> showtimes(@PathVariable Long id, @RequestParam String city,
                                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                         @RequestParam(required = false) Double lat, @RequestParam(required = false) Double lng,
                                                         @RequestParam(required = false) String language, @RequestParam(required = false) String format) {
        return ApiResponse.ok(showService.showtimesForMovie(id, city, date, lat, lng, language, format));
    }
}
