package com.cineverse.entity;

import com.cineverse.entity.enums.MovieStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Getter @Setter
@Entity
@Table(name = "movies")
public class Movie {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String title;
    private String slug;
    private String tagline;
    private String synopsis;
    @Column(name = "duration_min")
    private int durationMin;
    private String certificate;
    private LocalDate releaseDate;
    private String posterUrl;
    private String backdropUrl;
    private String trailerUrl;
    /** Comma separated list, e.g. "2D,3D,IMAX". */
    private String formats = "2D";
    @Enumerated(EnumType.STRING)
    private MovieStatus status = MovieStatus.NOW_SHOWING;
    private boolean featured;
    private boolean active = true;
    /** The Movie Database id, resolved by the media sync. */
    private Integer tmdbId;
    private java.math.BigDecimal imdbRating;
    private Integer imdbVotes;
    /** Optional curated collection, e.g. "CLASSICS". */
    private String collection;
    @Column(name = "top250_rank")
    private Integer top250Rank;
    /** ADMIN (created in the console), IMPORT (catalog migration) or DEMO (fictional launch data). */
    private String catalogSource = "ADMIN";
    private boolean runtimeEstimated;
    private boolean releaseDateEstimated;
    /** Set when an admin edits poster/trailer by hand; the media sync then leaves those fields alone. */
    private boolean mediaLocked;
    private Instant mediaSyncedAt;
    private String mediaSyncNote;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    @ManyToMany
    @JoinTable(name = "movie_genres", joinColumns = @JoinColumn(name = "movie_id"), inverseJoinColumns = @JoinColumn(name = "genre_id"))
    @OrderBy("name")
    private Set<Genre> genres = new LinkedHashSet<>();

    @ElementCollection
    @CollectionTable(name = "movie_languages", joinColumns = @JoinColumn(name = "movie_id"))
    @Column(name = "language")
    private Set<String> languages = new LinkedHashSet<>();

    @ManyToMany
    @JoinTable(name = "movie_directors", joinColumns = @JoinColumn(name = "movie_id"), inverseJoinColumns = @JoinColumn(name = "director_id"))
    private Set<Director> directors = new LinkedHashSet<>();

    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("billingOrder")
    private List<MovieCast> cast = new ArrayList<>();

    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder")
    private List<MovieImage> images = new ArrayList<>();

    /** Read-only link to the backend-maintained rating aggregate (used for sorting). */
    @OneToOne(fetch = FetchType.LAZY)
    @PrimaryKeyJoinColumn
    private Rating rating;

    @PreUpdate
    void touch() { updatedAt = Instant.now(); }

    public List<String> formatList() {
        return formats == null || formats.isBlank() ? List.of() : Arrays.stream(formats.split(",")).map(String::trim).toList();
    }
}
