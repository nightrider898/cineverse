package com.cineverse.controller;

import com.cineverse.common.ApiResponse;
import com.cineverse.config.AppProperties;
import com.cineverse.dto.CatalogDtos.*;
import com.cineverse.dto.EventDtos.CategoryResponse;
import com.cineverse.entity.enums.EventVertical;
import com.cineverse.service.CatalogService;
import com.cineverse.service.CinemaService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Catalog")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CatalogController {
    private final CatalogService catalogService;
    private final CinemaService cinemaService;
    private final AppProperties props;

    @GetMapping("/cities")
    public ApiResponse<List<CityResponse>> cities() {
        return ApiResponse.ok(catalogService.cities());
    }

    @GetMapping("/cities/nearest")
    public ApiResponse<CityResponse> nearest(@RequestParam double lat, @RequestParam double lng) {
        return ApiResponse.ok(catalogService.nearestCity(lat, lng));
    }

    @GetMapping("/meta/genres")
    public ApiResponse<List<GenreResponse>> genres() {
        return ApiResponse.ok(catalogService.genres());
    }

    @GetMapping("/meta/event-categories")
    public ApiResponse<List<CategoryResponse>> eventCategories(@RequestParam(required = false) EventVertical vertical) {
        return ApiResponse.ok(catalogService.eventCategories(vertical));
    }

    @GetMapping("/meta/config")
    public ApiResponse<Map<String, Object>> config() {
        return ApiResponse.ok(Map.of(
                "seatLockSeconds", props.seatLock().duration().toSeconds(),
                "maxSeatsPerBooking", props.seatLock().maxSeats(),
                "cancellationCutoffMinutes", props.cancellation().cutoffMinutesBeforeShow(),
                "convenienceFeePerTicket", props.pricing().convenienceFeePerTicket(),
                "taxRateOnFee", props.pricing().taxRateOnFee(),
                "currency", props.pricing().currency(),
                "mockPayments", props.payments().mockEnabled()));
    }

    @GetMapping("/food")
    public ApiResponse<List<FoodItemResponse>> food(@RequestParam(required = false) Long cinemaId) {
        return ApiResponse.ok(catalogService.foodMenu(cinemaId));
    }

    @GetMapping("/offers")
    public ApiResponse<List<OfferResponse>> offers(@RequestParam(required = false) String city) {
        return ApiResponse.ok(catalogService.offers(city));
    }

    @GetMapping("/venues")
    public ApiResponse<List<VenueSummary>> venues(@RequestParam String city) {
        return ApiResponse.ok(cinemaService.venues(city));
    }

    @GetMapping("/venues/{id}")
    public ApiResponse<VenueSummary> venue(@PathVariable Long id) {
        return ApiResponse.ok(cinemaService.venue(id));
    }
}
