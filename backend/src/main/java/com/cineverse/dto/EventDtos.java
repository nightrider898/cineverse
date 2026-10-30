package com.cineverse.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class EventDtos {
    private EventDtos() {}

    public record CategoryResponse(Long id, String code, String name, String vertical, String icon) {}

    public record EventResponse(Long id, String title, String slug, String vertical, CategoryResponse category,
                                String posterUrl, String bannerUrl, String venueName, String city,
                                Instant nextStartTime, BigDecimal minPrice, String artist, String teamHome, String teamAway,
                                String languages, String ageRestriction, boolean featured, boolean soldOut) {}

    public record TicketCategoryResponse(Long id, String name, String description, BigDecimal price, int capacity,
                                         int remaining, int maxPerBooking, boolean soldOut) {}

    public record SessionResponse(Long id, Instant startTime, Instant endTime, String status, List<TicketCategoryResponse> categories) {}

    public record EventDetailResponse(EventResponse event, String description, Integer durationMin,
                                      CatalogDtos.VenueSummary venue, List<SessionResponse> sessions) {}
}
