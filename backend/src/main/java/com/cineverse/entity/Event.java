package com.cineverse.entity;

import com.cineverse.entity.enums.EventStatus;
import com.cineverse.entity.enums.EventVertical;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.*;

@Getter @Setter
@Entity
@Table(name = "events")
public class Event {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String title;
    private String slug;
    private String description;
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "category_id")
    private EventCategory category;
    @Enumerated(EnumType.STRING)
    private EventVertical vertical;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id")
    private Venue venue;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "city_id")
    private City city;
    private String artist;
    private String teamHome;
    private String teamAway;
    private String languages;
    @Column(name = "duration_min")
    private Integer durationMin;
    private String ageRestriction;
    private String posterUrl;
    private String bannerUrl;
    private boolean featured;
    @Enumerated(EnumType.STRING)
    private EventStatus status = EventStatus.PUBLISHED;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("startTime")
    private List<EventSession> sessions = new ArrayList<>();

    @ManyToMany
    @JoinTable(name = "event_partners", joinColumns = @JoinColumn(name = "event_id"), inverseJoinColumns = @JoinColumn(name = "partner_id"))
    private Set<Partner> partners = new HashSet<>();

    @PreUpdate
    void touch() { updatedAt = Instant.now(); }
}
