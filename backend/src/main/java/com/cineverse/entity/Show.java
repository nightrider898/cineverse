package com.cineverse.entity;

import com.cineverse.entity.enums.ShowStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter @Setter
@Entity
@Table(name = "shows")
public class Show {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movie_id")
    private Movie movie;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cinema_id")
    private Cinema cinema;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "screen_id")
    private CinemaScreen screen;
    private LocalDate showDate;
    private Instant startTime;
    private Instant endTime;
    private String format = "2D";
    private String language;
    private BigDecimal basePrice;
    @Enumerated(EnumType.STRING)
    private ShowStatus status = ShowStatus.SCHEDULED;
    private Instant createdAt = Instant.now();
}
