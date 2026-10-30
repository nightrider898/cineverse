package com.cineverse.service;

import com.cineverse.dto.CatalogDtos.*;
import com.cineverse.entity.Actor;
import com.cineverse.entity.City;
import com.cineverse.entity.SearchHistory;
import com.cineverse.mapper.CatalogMapper;
import com.cineverse.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** Grouped search across movies, events, cinemas, people and venues (trigram-indexed ILIKE). */
@Service
@RequiredArgsConstructor
public class SearchService {
    private static final List<String> DEFAULT_TRENDING = List.of("Galactic Dawn", "Neon City", "Comedy Night", "IMAX", "Veera");

    private final MovieRepository movieRepository;
    private final EventRepository eventRepository;
    private final CinemaRepository cinemaRepository;
    private final ActorRepository actorRepository;
    private final VenueRepository venueRepository;
    private final SearchHistoryRepository historyRepository;
    private final CatalogService catalogService;
    private final EventService eventService;
    private final CatalogMapper mapper;
    private final Clock clock;

    @Transactional
    public SearchResponse search(String rawQuery, String citySlug, int limit, Long userId, boolean record) {
        String q = rawQuery == null ? "" : rawQuery.trim();
        if (q.length() < 2) {
            return new SearchResponse(q, List.of(), List.of(), List.of(), List.of(), List.of());
        }
        String term = q.length() > 60 ? q.substring(0, 60) : q;
        int n = Math.max(1, Math.min(limit, 20));
        City city = catalogService.resolveCity(citySlug);
        Long cityId = city == null ? null : city.getId();
        Instant now = Instant.now(clock);

        List<Actor> people = actorRepository.search(term, PageRequest.of(0, n));
        LinkedHashMap<Long, com.cineverse.entity.Movie> movies = new LinkedHashMap<>();
        movieRepository.search(term, PageRequest.of(0, n)).forEach(m -> movies.put(m.getId(), m));
        if (!people.isEmpty() && movies.size() < n) {
            movieRepository.findByActorIds(people.stream().map(Actor::getId).toList(), PageRequest.of(0, n)).forEach(m -> movies.putIfAbsent(m.getId(), m));
        }

        SearchResponse response = new SearchResponse(q,
                mapper.movies(movies.values().stream().limit(n).toList()),
                eventRepository.search(term, cityId, PageRequest.of(0, n)).stream().map(e -> eventService.card(e, now)).toList(),
                cinemaRepository.search(term, cityId, PageRequest.of(0, n)).stream().map(mapper::cinema).toList(),
                people.stream().map(mapper::person).toList(),
                venueRepository.search(term, cityId, PageRequest.of(0, n)).stream().map(mapper::venue).toList());

        if (record) {
            SearchHistory h = new SearchHistory();
            h.setUserId(userId);
            h.setQuery(term);
            h.setCityId(cityId);
            historyRepository.save(h);
        }
        return response;
    }

    @Transactional(readOnly = true)
    public SearchSuggestions suggestions(Long userId) {
        List<String> recent = userId == null ? List.of() : historyRepository.recentForUser(userId, PageRequest.of(0, 6));
        List<String> trending = new ArrayList<>(historyRepository.trending(Instant.now(clock).minus(Duration.ofDays(7)), PageRequest.of(0, 8)));
        for (String d : DEFAULT_TRENDING) {
            if (trending.size() >= 6) break;
            if (trending.stream().noneMatch(t -> t.equalsIgnoreCase(d))) trending.add(d);
        }
        return new SearchSuggestions(recent, trending);
    }
}
