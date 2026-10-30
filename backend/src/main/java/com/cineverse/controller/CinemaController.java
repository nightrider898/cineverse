package com.cineverse.controller;

import com.cineverse.common.ApiResponse;
import com.cineverse.dto.CatalogDtos.*;
import com.cineverse.service.CinemaService;
import com.cineverse.service.ShowService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Cinemas")
@RestController
@RequestMapping("/api/v1/cinemas")
@RequiredArgsConstructor
public class CinemaController {
    private final CinemaService cinemaService;
    private final ShowService showService;

    @GetMapping
    public ApiResponse<List<CinemaSummary>> list(@RequestParam String city, @RequestParam(required = false) Double lat,
                                                 @RequestParam(required = false) Double lng) {
        return ApiResponse.ok(cinemaService.list(city, lat, lng));
    }

    @GetMapping("/{id}")
    public ApiResponse<CinemaDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.ok(cinemaService.detail(id));
    }

    @GetMapping("/{id}/showtimes")
    public ApiResponse<CinemaShowtimesResponse> showtimes(@PathVariable Long id,
                                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.ok(showService.showtimesForCinema(id, date));
    }
}
