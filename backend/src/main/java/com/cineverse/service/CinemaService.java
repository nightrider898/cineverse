package com.cineverse.service;

import com.cineverse.dto.CatalogDtos.*;
import com.cineverse.entity.City;
import com.cineverse.exception.ApiException;
import com.cineverse.mapper.CatalogMapper;
import com.cineverse.repository.CinemaRepository;
import com.cineverse.repository.CinemaScreenRepository;
import com.cineverse.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CinemaService {
    private final CinemaRepository cinemaRepository;
    private final CinemaScreenRepository screenRepository;
    private final VenueRepository venueRepository;
    private final CatalogService catalogService;
    private final CatalogMapper mapper;

    public List<CinemaSummary> list(String citySlug, Double lat, Double lng) {
        City city = catalogService.resolveCity(citySlug);
        if (city == null) throw new ApiException(com.cineverse.exception.ErrorCode.BAD_REQUEST, "city is required");
        return cinemaRepository.findByCityIdAndActiveTrueOrderByName(city.getId()).stream()
                .map(c -> mapper.cinema(c, CatalogMapper.distanceKm(lat, lng, c.getLatitude(), c.getLongitude())))
                .sorted(Comparator.comparing((CinemaSummary c) -> c.distanceKm() == null ? Double.MAX_VALUE : c.distanceKm()).thenComparing(CinemaSummary::name))
                .toList();
    }

    public CinemaDetailResponse detail(Long id) {
        var c = cinemaRepository.findById(id).filter(x -> x.isActive()).orElseThrow(() -> ApiException.notFound("Cinema"));
        return new CinemaDetailResponse(mapper.cinema(c), screenRepository.findByCinemaIdOrderByName(id).stream()
                .filter(s -> s.isActive()).map(mapper::screen).toList());
    }

    public VenueSummary venue(Long id) {
        return venueRepository.findById(id).filter(v -> v.isActive()).map(mapper::venue).orElseThrow(() -> ApiException.notFound("Venue"));
    }

    public List<VenueSummary> venues(String citySlug) {
        City city = catalogService.resolveCity(citySlug);
        if (city == null) throw new ApiException(com.cineverse.exception.ErrorCode.BAD_REQUEST, "city is required");
        return venueRepository.findByCityIdAndActiveTrueOrderByName(city.getId()).stream().map(mapper::venue).toList();
    }
}
