package com.cineverse.service;

import com.cineverse.dto.EngagementDtos.*;
import com.cineverse.entity.Favorite;
import com.cineverse.entity.enums.FavoriteType;
import com.cineverse.exception.ApiException;
import com.cineverse.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FavoriteService {
    private final FavoriteRepository favoriteRepository;
    private final MovieRepository movieRepository;
    private final CinemaRepository cinemaRepository;
    private final EventRepository eventRepository;

    @Transactional(readOnly = true)
    public List<FavoriteResponse> list(Long userId) {
        return favoriteRepository.findByUserIdOrderByCreatedAtDesc(userId).stream().map(this::toResponse)
                .filter(f -> f.title() != null).toList();
    }

    @Transactional
    public FavoriteResponse add(Long userId, FavoriteRequest req) {
        assertExists(req.targetType(), req.targetId());
        Favorite f = favoriteRepository.findByUserIdAndTargetTypeAndTargetId(userId, req.targetType(), req.targetId()).orElseGet(() -> {
            Favorite x = new Favorite();
            x.setUserId(userId);
            x.setTargetType(req.targetType());
            x.setTargetId(req.targetId());
            return favoriteRepository.save(x);
        });
        return toResponse(f);
    }

    @Transactional
    public void remove(Long userId, Long id) {
        Favorite f = favoriteRepository.findById(id).filter(x -> x.getUserId().equals(userId)).orElseThrow(() -> ApiException.notFound("Favorite"));
        favoriteRepository.delete(f);
    }

    private void assertExists(FavoriteType type, Long id) {
        boolean exists = switch (type) {
            case MOVIE -> movieRepository.existsById(id);
            case CINEMA -> cinemaRepository.existsById(id);
            case EVENT -> eventRepository.existsById(id);
        };
        if (!exists) throw ApiException.notFound(type.name().charAt(0) + type.name().substring(1).toLowerCase());
    }

    private FavoriteResponse toResponse(Favorite f) {
        return switch (f.getTargetType()) {
            case MOVIE -> movieRepository.findById(f.getTargetId()).map(m -> new FavoriteResponse(f.getId(), "MOVIE", m.getId(), m.getTitle(),
                    String.join(" · ", m.getLanguages()), m.getPosterUrl(), "/movies/" + m.getId(), f.getCreatedAt()))
                    .orElse(new FavoriteResponse(f.getId(), "MOVIE", f.getTargetId(), null, null, null, null, f.getCreatedAt()));
            case CINEMA -> cinemaRepository.findById(f.getTargetId()).map(c -> new FavoriteResponse(f.getId(), "CINEMA", c.getId(), c.getName(),
                    c.getAddress(), null, "/cinemas/" + c.getId(), f.getCreatedAt()))
                    .orElse(new FavoriteResponse(f.getId(), "CINEMA", f.getTargetId(), null, null, null, null, f.getCreatedAt()));
            case EVENT -> eventRepository.findById(f.getTargetId()).map(e -> new FavoriteResponse(f.getId(), "EVENT", e.getId(), e.getTitle(),
                    e.getVenue().getName(), e.getPosterUrl(), "/events/" + e.getId(), f.getCreatedAt()))
                    .orElse(new FavoriteResponse(f.getId(), "EVENT", f.getTargetId(), null, null, null, null, f.getCreatedAt()));
        };
    }
}
