package com.cineverse.service;

import com.cineverse.dto.EngagementDtos.*;
import com.cineverse.entity.Watchlist;
import com.cineverse.exception.ApiException;
import com.cineverse.mapper.CatalogMapper;
import com.cineverse.repository.MovieRepository;
import com.cineverse.repository.WatchlistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WatchlistService {
    private final WatchlistRepository watchlistRepository;
    private final MovieRepository movieRepository;
    private final CatalogMapper mapper;

    @Transactional(readOnly = true)
    public List<WatchlistResponse> list(Long userId) {
        return watchlistRepository.findForUser(userId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public WatchlistResponse add(Long userId, WatchlistRequest req) {
        var movie = movieRepository.findById(req.movieId()).orElseThrow(() -> ApiException.notFound("Movie"));
        Watchlist w = watchlistRepository.findByUserIdAndMovieId(userId, movie.getId()).orElseGet(() -> {
            Watchlist x = new Watchlist();
            x.setUserId(userId);
            x.setMovie(movie);
            return x;
        });
        if (req.remindMe() != null) w.setRemindMe(req.remindMe());
        return toResponse(watchlistRepository.save(w));
    }

    @Transactional
    public void remove(Long userId, Long movieId) {
        watchlistRepository.findByUserIdAndMovieId(userId, movieId).ifPresent(watchlistRepository::delete);
    }

    private WatchlistResponse toResponse(Watchlist w) {
        return new WatchlistResponse(w.getId(), mapper.movie(w.getMovie()), w.isRemindMe(), w.getMovie().getReleaseDate(), w.getCreatedAt());
    }
}
