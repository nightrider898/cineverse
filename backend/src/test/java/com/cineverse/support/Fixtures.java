package com.cineverse.support;

import com.cineverse.entity.*;
import com.cineverse.entity.enums.MovieStatus;
import com.cineverse.repository.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Creates users and shows directly, each test on its own future time slot so tests never interfere. */
@Component
public class Fixtures {
    public static final String PASSWORD = "Passw0rd!";
    private static final AtomicInteger SLOT = new AtomicInteger();

    private final UserRepository users;
    private final RoleRepository roles;
    private final MovieRepository movies;
    private final CinemaScreenRepository screens;
    private final ShowRepository shows;
    private final ShowSeatRepository showSeats;
    private final PasswordEncoder encoder;

    public Fixtures(UserRepository users, RoleRepository roles, MovieRepository movies, CinemaScreenRepository screens,
                    ShowRepository shows, ShowSeatRepository showSeats, PasswordEncoder encoder) {
        this.users = users;
        this.roles = roles;
        this.movies = movies;
        this.screens = screens;
        this.shows = shows;
        this.showSeats = showSeats;
        this.encoder = encoder;
    }

    @Transactional
    public User user(String role) {
        User u = new User();
        u.setEmail(role.toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 8) + "@test.cineverse.com");
        u.setFullName("Test " + role);
        u.setPasswordHash(encoder.encode(PASSWORD));
        u.getRoles().addAll(roles.findByNameIn(List.of(role)));
        return users.save(u);
    }

    /** A show starting {@code startsIn} from now on the first Hyderabad screen, isolated in time from other tests. */
    @Transactional
    public Show show(Duration startsIn) {
        Movie movie = movies.findByActiveTrueAndStatusOrderByReleaseDateAsc(MovieStatus.NOW_SHOWING).get(0);
        CinemaScreen screen = screens.findAll().stream().sorted((a, b) -> Long.compare(a.getId(), b.getId())).toList()
                .get(SLOT.get() % 20);
        Instant start = Instant.now().plus(startsIn).plus(Duration.ofDays(SLOT.incrementAndGet() * 2L));
        return create(movie, screen, start);
    }

    /** A show exactly {@code startsIn} from now (used for cancellation-deadline tests). */
    @Transactional
    public Show showAt(Duration startsIn, int screenIndex) {
        Movie movie = movies.findByActiveTrueAndStatusOrderByReleaseDateAsc(MovieStatus.NOW_SHOWING).get(1);
        CinemaScreen screen = screens.findAll().stream().sorted((a, b) -> Long.compare(a.getId(), b.getId())).toList().get(screenIndex);
        return create(movie, screen, Instant.now().plus(startsIn));
    }

    private Show create(Movie movie, CinemaScreen screen, Instant start) {
        Show s = new Show();
        s.setMovie(movie);
        s.setCinema(screen.getCinema());
        s.setScreen(screen);
        s.setShowDate(LocalDate.ofInstant(start, ZoneId.of("Asia/Kolkata")));
        s.setStartTime(start);
        s.setEndTime(start.plus(Duration.ofMinutes(movie.getDurationMin() + 20L)));
        s.setFormat("2D");
        s.setLanguage(movie.getLanguages().iterator().next());
        s.setBasePrice(BigDecimal.valueOf(200));
        shows.saveAndFlush(s);
        // tests read these after the transaction ends, so load them now instead of leaving lazy proxies
        org.hibernate.Hibernate.initialize(screen.getCinema());
        org.hibernate.Hibernate.initialize(screen.getCinema().getCity());
        showSeats.generateForShow(s.getId(), screen.getId(), s.getBasePrice());
        return s;
    }
}