package com.cineverse.service;

import com.cineverse.dto.EventDtos.*;
import com.cineverse.entity.*;
import com.cineverse.entity.enums.EventStatus;
import com.cineverse.entity.enums.EventVertical;
import com.cineverse.exception.ApiException;
import com.cineverse.mapper.CatalogMapper;
import com.cineverse.repository.EventRepository;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventService {
    private final EventRepository eventRepository;
    private final CatalogService catalogService;
    private final CatalogMapper mapper;
    private final Clock clock;

    public Page<EventResponse> list(EventVertical vertical, String citySlug, String category, LocalDate date, String q, int page, int size) {
        Instant now = Instant.now(clock);
        City city = catalogService.resolveCity(citySlug);
        Specification<Event> spec = (root, query, cb) -> cb.equal(root.get("status"), EventStatus.PUBLISHED);
        if (vertical != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("vertical"), vertical));
        if (city != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("city").get("id"), city.getId()));
        if (category != null && !category.isBlank()) {
            String code = category.trim().toUpperCase();
            spec = spec.and((root, query, cb) -> cb.equal(root.get("category").get("code"), code));
        }
        if (q != null && !q.isBlank()) {
            String like = "%" + q.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("title")), like));
        }
        ZoneId zone = ZoneId.of("Asia/Kolkata");
        Instant from = date == null ? now : date.atStartOfDay(zone).toInstant().isBefore(now) ? now : date.atStartOfDay(zone).toInstant();
        Instant to = date == null ? null : date.plusDays(1).atStartOfDay(zone).toInstant();
        spec = spec.and((root, query, cb) -> {
            Subquery<Long> sq = query.subquery(Long.class);
            Root<EventSession> s = sq.from(EventSession.class);
            List<Predicate> ps = new ArrayList<>(List.of(cb.equal(s.get("event"), root), cb.greaterThan(s.get("startTime"), from)));
            if (to != null) ps.add(cb.lessThan(s.get("startTime"), to));
            sq.select(s.get("id")).where(ps.toArray(Predicate[]::new));
            return cb.exists(sq);
        });
        Page<Event> events = eventRepository.findAll(spec, PageRequest.of(page, Math.min(size, 50),
                Sort.by(Sort.Order.desc("featured"), Sort.Order.asc("title"))));
        List<EventResponse> cards = events.getContent().stream().map(e -> card(e, now))
                .sorted(Comparator.comparing(EventResponse::nextStartTime, Comparator.nullsLast(Comparator.naturalOrder()))).toList();
        return new PageImpl<>(cards, events.getPageable(), events.getTotalElements());
    }

    public EventDetailResponse detail(Long id) {
        Instant now = Instant.now(clock);
        Event e = eventRepository.findById(id).filter(x -> x.getStatus() != EventStatus.DRAFT).orElseThrow(() -> ApiException.notFound("Event"));
        List<SessionResponse> sessions = e.getSessions().stream().filter(s -> s.getStartTime().isAfter(now))
                .map(s -> new SessionResponse(s.getId(), s.getStartTime(), s.getEndTime(), s.getStatus(),
                        s.getTicketCategories().stream().map(c -> new TicketCategoryResponse(c.getId(), c.getName(), c.getDescription(),
                                c.getPrice(), c.getCapacity(), c.remaining(), c.getMaxPerBooking(), c.remaining() == 0)).toList()))
                .toList();
        return new EventDetailResponse(card(e, now), e.getDescription(), e.getDurationMin(), mapper.venue(e.getVenue()), sessions);
    }

    public EventResponse card(Event e, Instant now) {
        List<EventSession> upcoming = e.getSessions().stream().filter(s -> s.getStartTime().isAfter(now)).toList();
        Instant next = upcoming.stream().map(EventSession::getStartTime).min(Comparator.naturalOrder()).orElse(null);
        BigDecimal minPrice = upcoming.stream().flatMap(s -> s.getTicketCategories().stream()).map(EventTicketCategory::getPrice)
                .min(Comparator.naturalOrder()).orElse(null);
        boolean soldOut = !upcoming.isEmpty() && upcoming.stream().flatMap(s -> s.getTicketCategories().stream()).allMatch(c -> c.remaining() == 0);
        return new EventResponse(e.getId(), e.getTitle(), e.getSlug(), e.getVertical().name(), mapper.category(e.getCategory()),
                e.getPosterUrl(), e.getBannerUrl(), e.getVenue().getName(), e.getCity().getName(), next, minPrice, e.getArtist(),
                e.getTeamHome(), e.getTeamAway(), e.getLanguages(), e.getAgeRestriction(), e.isFeatured(), soldOut);
    }
}
