package com.cineverse.service;

import com.cineverse.config.CacheConfig;
import com.cineverse.dto.CatalogDtos.*;
import com.cineverse.dto.EventDtos;
import com.cineverse.entity.City;
import com.cineverse.entity.enums.EventVertical;
import com.cineverse.exception.ApiException;
import com.cineverse.mapper.CatalogMapper;
import com.cineverse.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/** Slow-changing reference data: cities, genres, categories, food menus, offers. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogService {
    private final CityRepository cityRepository;
    private final GenreRepository genreRepository;
    private final EventCategoryRepository eventCategoryRepository;
    private final FoodItemRepository foodItemRepository;
    private final OfferRepository offerRepository;
    private final CatalogMapper mapper;
    private final Clock clock;

    @Cacheable(CacheConfig.CITIES)
    public List<CityResponse> cities() {
        return cityRepository.findByActiveTrueOrderByPopularDescNameAsc().stream().map(mapper::city).toList();
    }

    /** Accepts a slug ("hyderabad"), a name ("Hyderabad") or a numeric id. */
    public City resolveCity(String city) {
        if (city == null || city.isBlank()) return null;
        String c = city.trim();
        if (c.chars().allMatch(Character::isDigit)) {
            return cityRepository.findById(Long.valueOf(c)).orElseThrow(() -> ApiException.notFound("City"));
        }
        return cityRepository.findBySlugIgnoreCase(c).or(() -> cityRepository.findByNameIgnoreCase(c))
                .orElseThrow(() -> ApiException.notFound("City"));
    }

    /** Nearest supported city to a coordinate, for "detect my location". */
    public CityResponse nearestCity(double lat, double lng) {
        return cityRepository.findByActiveTrueOrderByPopularDescNameAsc().stream()
                .filter(c -> c.getLatitude() != null)
                .min(Comparator.comparingDouble(c -> CatalogMapper.distanceKm(lat, lng, c.getLatitude(), c.getLongitude())))
                .map(mapper::city).orElseThrow(() -> ApiException.notFound("City"));
    }

    @Cacheable(CacheConfig.GENRES)
    public List<GenreResponse> genres() {
        return genreRepository.findAllByOrderByNameAsc().stream().map(mapper::genre).toList();
    }

    @Cacheable(CacheConfig.EVENT_CATEGORIES)
    public List<EventDtos.CategoryResponse> eventCategories(EventVertical vertical) {
        var list = vertical == null ? eventCategoryRepository.findAll() : eventCategoryRepository.findByVerticalOrderByName(vertical);
        return list.stream().map(mapper::category).toList();
    }

    @Cacheable(value = CacheConfig.FOOD_MENU, key = "#cinemaId == null ? 0 : #cinemaId")
    public List<FoodItemResponse> foodMenu(Long cinemaId) {
        return foodItemRepository.menuForCinema(cinemaId).stream().map(mapper::food).toList();
    }

    public List<OfferResponse> offers(String city) {
        City c = resolveCity(city);
        return offerRepository.activeForCity(c == null ? null : c.getId(), Instant.now(clock)).stream().map(mapper::offer).toList();
    }
}
