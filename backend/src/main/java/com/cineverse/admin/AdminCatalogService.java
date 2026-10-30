package com.cineverse.admin;

import com.cineverse.admin.AdminDtos.*;
import com.cineverse.booking.BookingLifecycleService;
import com.cineverse.booking.CancellationService;
import com.cineverse.config.AppProperties;
import com.cineverse.config.CacheConfig;
import com.cineverse.entity.*;
import com.cineverse.entity.enums.*;
import com.cineverse.exception.ApiException;
import com.cineverse.exception.ErrorCode;
import com.cineverse.media.YouTubeUrls;
import com.cineverse.repository.*;
import com.cineverse.service.AuditService;
import com.cineverse.service.ShowService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/** Admin / partner management of the catalog: movies, cinemas, screens and layouts, shows, events. */
@Service
@RequiredArgsConstructor
public class AdminCatalogService {
    private final MovieRepository movieRepository;
    private final GenreRepository genreRepository;
    private final ActorRepository actorRepository;
    private final DirectorRepository directorRepository;
    private final RatingRepository ratingRepository;
    private final CinemaRepository cinemaRepository;
    private final CityRepository cityRepository;
    private final LocationRepository locationRepository;
    private final CinemaScreenRepository screenRepository;
    private final ScreenSeatRepository screenSeatRepository;
    private final SeatTypeRepository seatTypeRepository;
    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;
    private final BookingRepository bookingRepository;
    private final VenueRepository venueRepository;
    private final EventRepository eventRepository;
    private final EventCategoryRepository eventCategoryRepository;
    private final EventSessionRepository eventSessionRepository;
    private final CancellationService cancellationService;
    private final BookingLifecycleService lifecycle;
    private final ShowService showService;
    private final AdminAccess access;
    private final AuditService audit;
    private final AppProperties props;
    private final EntityManager entityManager;
    private final Clock clock;

    // =================================================================== movies

    @Transactional(readOnly = true)
    public Page<AdminMovieResponse> movies(String q, int page, int size) {
        Specification<Movie> spec = (root, query, cb) -> q == null || q.isBlank() ? cb.conjunction()
                : cb.like(cb.lower(root.get("title")), "%" + q.trim().toLowerCase() + "%");
        return movieRepository.findAll(spec, PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "releaseDate")))
                .map(this::movieResponse);
    }

    @Transactional(readOnly = true)
    public AdminMovieResponse movie(Long id) {
        return movieResponse(movieRepository.findById(id).orElseThrow(() -> ApiException.notFound("Movie")));
    }

    @Transactional
    @CacheEvict(value = CacheConfig.MOVIE_DETAIL, allEntries = true)
    public AdminMovieResponse createMovie(MovieUpsertRequest req) {
        access.requireAdmin();
        Movie m = new Movie();
        m.setSlug(Slugs.unique(req.title(), movieRepository::existsBySlug));
        applyMovie(m, req);
        movieRepository.save(m);
        Rating r = new Rating();
        r.setMovieId(m.getId());
        ratingRepository.save(r);
        audit.record("ADMIN_CREATED_MOVIE", "MOVIE", m.getId(), Map.of("title", m.getTitle()));
        return movieResponse(m);
    }

    @Transactional
    @CacheEvict(value = CacheConfig.MOVIE_DETAIL, allEntries = true)
    public AdminMovieResponse updateMovie(Long id, MovieUpsertRequest req) {
        access.requireAdmin();
        Movie m = movieRepository.findById(id).orElseThrow(() -> ApiException.notFound("Movie"));
        applyMovie(m, req);
        audit.record("ADMIN_UPDATED_MOVIE", "MOVIE", id, Map.of("title", m.getTitle()));
        return movieResponse(m);
    }

    @Transactional
    @CacheEvict(value = CacheConfig.MOVIE_DETAIL, allEntries = true)
    public void deactivateMovie(Long id) {
        access.requireAdmin();
        Movie m = movieRepository.findById(id).orElseThrow(() -> ApiException.notFound("Movie"));
        m.setActive(false);
        m.setStatus(MovieStatus.ARCHIVED);
        audit.record("ADMIN_DEACTIVATED_MOVIE", "MOVIE", id, null);
    }

    private void applyMovie(Movie m, MovieUpsertRequest req) {
        m.setTitle(req.title().trim());
        m.setTagline(blankToNull(req.tagline()));
        m.setSynopsis(req.synopsis().trim());
        m.setDurationMin(req.durationMin());
        m.setCertificate(req.certificate());
        m.setReleaseDate(req.releaseDate());
        String poster = blankToNull(req.posterUrl());
        String backdrop = blankToNull(req.backdropUrl());
        String trailer = YouTubeUrls.normalise(req.trailerUrl()); // any YouTube link -> embeddable URL
        boolean mediaEdited = !Objects.equals(poster, m.getPosterUrl()) || !Objects.equals(backdrop, m.getBackdropUrl())
                || !Objects.equals(trailer, m.getTrailerUrl());
        if (mediaEdited && (poster != null || trailer != null)) {
            m.setMediaLocked(true); // an admin chose these by hand: the TMDB sync must not overwrite them
        } else if (req.mediaLocked() != null) {
            m.setMediaLocked(req.mediaLocked()); // explicit lock / unlock from the console
        }
        m.setPosterUrl(poster);
        m.setBackdropUrl(backdrop);
        m.setTrailerUrl(trailer);
        m.setFormats(String.join(",", new LinkedHashSet<>(req.formats())));
        m.setStatus(MovieStatus.valueOf(req.status()));
        m.setFeatured(req.featured());
        m.setActive(req.active());
        m.getLanguages().clear();
        req.languages().forEach(l -> m.getLanguages().add(l.trim()));

        Map<String, Genre> genres = genreRepository.findAll().stream().collect(Collectors.toMap(g -> g.getName().toLowerCase(), g -> g));
        m.getGenres().clear();
        for (String g : req.genres()) {
            Genre genre = genres.get(g.trim().toLowerCase());
            if (genre == null) {
                genre = new Genre();
                genre.setName(g.trim());
                genre.setSlug(Slugs.unique(g, s -> genreRepository.findAll().stream().anyMatch(x -> x.getSlug().equals(s))));
                genreRepository.save(genre);
            }
            m.getGenres().add(genre);
        }
        m.getDirectors().clear();
        if (req.directors() != null) {
            for (String d : req.directors()) {
                Director director = directorRepository.findFirstByNameIgnoreCase(d.trim()).orElseGet(() -> {
                    Director x = new Director();
                    x.setName(d.trim());
                    return directorRepository.save(x);
                });
                m.getDirectors().add(director);
            }
        }
        m.getCast().clear();
        if (req.cast() != null) {
            int order = 1;
            for (CastInput c : req.cast()) {
                Actor actor = actorRepository.findFirstByNameIgnoreCase(c.name().trim()).orElseGet(() -> {
                    Actor a = new Actor();
                    a.setName(c.name().trim());
                    return actorRepository.save(a);
                });
                MovieCast mc = new MovieCast();
                mc.setMovie(m);
                mc.setActor(actor);
                mc.setCharacterName(blankToNull(c.character()));
                mc.setCrewRole(blankToNull(c.crewRole()));
                mc.setBillingOrder(order++);
                m.getCast().add(mc);
            }
        }
    }

    private AdminMovieResponse movieResponse(Movie m) {
        Rating r = ratingRepository.findById(m.getId()).orElse(null);
        return new AdminMovieResponse(m.getId(), m.getTitle(), m.getSlug(), m.getTagline(), m.getSynopsis(), m.getDurationMin(),
                m.getCertificate(), m.getReleaseDate(), m.getPosterUrl(), m.getBackdropUrl(), m.getTrailerUrl(), m.formatList(),
                m.getStatus().name(), m.isFeatured(), m.isActive(), List.copyOf(m.getLanguages()),
                m.getGenres().stream().map(Genre::getName).toList(), m.getDirectors().stream().map(Director::getName).toList(),
                m.getCast().stream().map(c -> new CastInput(c.getActor().getName(), c.getCharacterName(), c.getCrewRole())).toList(),
                r == null ? BigDecimal.ZERO : r.getAverage(), r == null ? 0 : r.getTotalVotes(),
                m.getTmdbId(), m.isMediaLocked(), m.getMediaSyncedAt(), m.getMediaSyncNote(), m.getCollection(),
                m.getImdbRating(), m.getCatalogSource());
    }

    /** Coverage of posters/trailers across active movies, for the admin media panel. */
    @Transactional(readOnly = true)
    public Map<String, Object> mediaStatus(boolean running) {
        access.requireAdmin();
        java.util.function.Function<String, Long> count = where -> entityManager
                .createQuery("select count(m) from Movie m where m.active = true" + where, Long.class).getSingleResult();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("running", running);
        out.put("tmdbConfigured", props.media() != null && props.media().tmdbConfigured());
        out.put("youtubeConfigured", props.media() != null && props.media().youtubeConfigured());
        out.put("activeMovies", count.apply(""));
        out.put("withPoster", count.apply(" and m.posterUrl is not null"));
        out.put("withTrailer", count.apply(" and m.trailerUrl is not null"));
        out.put("synced", count.apply(" and m.mediaSyncedAt is not null"));
        out.put("matchedOnTmdb", count.apply(" and m.tmdbId is not null"));
        return out;
    }

    // =================================================================== cinemas & venues

    @Transactional(readOnly = true)
    public List<AdminCinemaResponse> cinemas(Long cityId) {
        List<Long> scope = access.scopedCinemaIds();
        return cinemaRepository.adminList(cityId, PageRequest.of(0, 500)).stream()
                .filter(c -> scope == null || scope.contains(c.getId())).map(this::cinemaResponse).toList();
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CINEMAS_BY_CITY, allEntries = true)
    public AdminCinemaResponse createCinema(CinemaUpsertRequest req) {
        access.requireAdmin();
        Cinema c = new Cinema();
        c.setSlug(Slugs.unique(req.name() + "-" + cityRepository.findById(req.cityId()).map(City::getSlug).orElse(""), cinemaRepository::existsBySlug));
        applyCinema(c, req);
        cinemaRepository.save(c);
        audit.record("ADMIN_CREATED_CINEMA", "CINEMA", c.getId(), Map.of("name", c.getName()));
        return cinemaResponse(c);
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CINEMAS_BY_CITY, allEntries = true)
    public AdminCinemaResponse updateCinema(Long id, CinemaUpsertRequest req) {
        access.requireCinemaAccess(id);
        Cinema c = cinemaRepository.findById(id).orElseThrow(() -> ApiException.notFound("Cinema"));
        applyCinema(c, req);
        audit.record("ADMIN_UPDATED_CINEMA", "CINEMA", id, null);
        return cinemaResponse(c);
    }

    @Transactional
    public void deactivateCinema(Long id) {
        access.requireAdmin();
        Cinema c = cinemaRepository.findById(id).orElseThrow(() -> ApiException.notFound("Cinema"));
        c.setActive(false);
        audit.record("ADMIN_DEACTIVATED_CINEMA", "CINEMA", id, null);
    }

    private void applyCinema(Cinema c, CinemaUpsertRequest req) {
        City city = cityRepository.findById(req.cityId()).orElseThrow(() -> ApiException.notFound("City"));
        c.setName(req.name().trim());
        c.setCity(city);
        c.setAddress(req.address().trim());
        c.setLatitude(req.latitude());
        c.setLongitude(req.longitude());
        c.setFacilities(req.facilities() == null ? "" : String.join(",", req.facilities()));
        c.setAllowPayAtVenue(req.allowPayAtVenue());
        c.setCancellationAllowed(req.cancellationAllowed());
        c.setActive(req.active());
        if (req.locality() != null && !req.locality().isBlank()) {
            Location loc = locationRepository.findByCityIdOrderByName(city.getId()).stream()
                    .filter(l -> l.getName().equalsIgnoreCase(req.locality().trim())).findFirst().orElseGet(() -> {
                        Location l = new Location();
                        l.setCity(city);
                        l.setName(req.locality().trim());
                        return locationRepository.save(l);
                    });
            c.setLocation(loc);
        }
    }

    private AdminCinemaResponse cinemaResponse(Cinema c) {
        return new AdminCinemaResponse(c.getId(), c.getName(), c.getSlug(), c.getCity().getId(), c.getCity().getName(),
                c.getLocation() == null ? null : c.getLocation().getName(), c.getAddress(), c.getLatitude(), c.getLongitude(),
                c.facilityList(), c.isAllowPayAtVenue(), c.isCancellationAllowed(), c.isActive(),
                screenRepository.findByCinemaIdOrderByName(c.getId()).size());
    }

    @Transactional(readOnly = true)
    public List<com.cineverse.dto.CatalogDtos.VenueSummary> venues() {
        return venueRepository.findAll(Sort.by("name")).stream().map(v -> new com.cineverse.dto.CatalogDtos.VenueSummary(v.getId(), v.getName(),
                v.getSlug(), v.getAddress(), v.getCity().getName(), v.getCapacity(),
                v.getFacilities() == null || v.getFacilities().isBlank() ? List.of() : List.of(v.getFacilities().split(",")),
                v.getLatitude(), v.getLongitude(), v.isAllowPayAtVenue())).toList();
    }

    @Transactional
    public Long upsertVenue(Long id, VenueUpsertRequest req) {
        access.requireAdmin();
        Venue v = id == null ? new Venue() : venueRepository.findById(id).orElseThrow(() -> ApiException.notFound("Venue"));
        City city = cityRepository.findById(req.cityId()).orElseThrow(() -> ApiException.notFound("City"));
        if (id == null) v.setSlug(Slugs.unique(req.name() + "-" + city.getSlug(), venueRepository::existsBySlug));
        v.setName(req.name().trim());
        v.setCity(city);
        v.setAddress(req.address().trim());
        v.setLatitude(req.latitude());
        v.setLongitude(req.longitude());
        v.setCapacity(req.capacity());
        v.setFacilities(req.facilities() == null ? "" : String.join(",", req.facilities()));
        v.setImageUrl(blankToNull(req.imageUrl()));
        v.setAllowPayAtVenue(req.allowPayAtVenue());
        v.setActive(req.active());
        venueRepository.save(v);
        audit.record(id == null ? "ADMIN_CREATED_VENUE" : "ADMIN_UPDATED_VENUE", "VENUE", v.getId(), null);
        return v.getId();
    }

    // =================================================================== screens & seat layouts

    @Transactional(readOnly = true)
    public List<AdminScreenResponse> screens(Long cinemaId) {
        access.requireCinemaAccess(cinemaId);
        return screenRepository.findByCinemaIdOrderByName(cinemaId).stream().map(this::screenResponse).toList();
    }

    @Transactional(readOnly = true)
    public AdminScreenResponse screen(Long id) {
        CinemaScreen s = screenRepository.findById(id).orElseThrow(() -> ApiException.notFound("Screen"));
        access.requireCinemaAccess(s.getCinema().getId());
        return screenResponse(s);
    }

    @Transactional(readOnly = true)
    public List<SeatTypeResponse> seatTypes() {
        return seatTypeRepository.findAllByOrderBySortOrderAsc().stream()
                .map(t -> new SeatTypeResponse(t.getId(), t.getCode(), t.getName(), t.getPriceDelta())).toList();
    }

    @Transactional
    public AdminScreenResponse createScreen(ScreenUpsertRequest req) {
        access.requireCinemaAccess(req.cinemaId());
        CinemaScreen s = new CinemaScreen();
        s.setCinema(cinemaRepository.findById(req.cinemaId()).orElseThrow(() -> ApiException.notFound("Cinema")));
        applyScreen(s, req);
        screenRepository.save(s);
        audit.record("ADMIN_CREATED_SCREEN", "SCREEN", s.getId(), Map.of("cinemaId", req.cinemaId()));
        return screenResponse(s);
    }

    @Transactional
    public AdminScreenResponse updateScreen(Long id, ScreenUpsertRequest req) {
        CinemaScreen s = screenRepository.findById(id).orElseThrow(() -> ApiException.notFound("Screen"));
        access.requireCinemaAccess(s.getCinema().getId());
        applyScreen(s, req);
        audit.record("ADMIN_UPDATED_SCREEN", "SCREEN", id, null);
        return screenResponse(s);
    }

    private void applyScreen(CinemaScreen s, ScreenUpsertRequest req) {
        s.setName(req.name().trim());
        s.setScreenType(req.screenType());
        s.setFormats(String.join(",", req.formats()));
        s.setActive(req.active());
    }

    /**
     * Saves a layout from the visual configurator. Seats keep their identity by label so historical
     * show inventories stay intact; seats removed from the layout but referenced by past shows are disabled.
     */
    @Transactional
    public AdminScreenResponse saveLayout(Long screenId, SeatLayoutRequest req) {
        CinemaScreen screen = screenRepository.findById(screenId).orElseThrow(() -> ApiException.notFound("Screen"));
        access.requireCinemaAccess(screen.getCinema().getId());
        if (showRepository.existsByScreenIdAndStartTimeAfter(screenId, Instant.now(clock))) {
            throw new ApiException(ErrorCode.CONFLICT, "Cancel or finish the scheduled shows on this screen before changing its layout");
        }
        Map<String, SeatType> types = seatTypeRepository.findAll().stream().collect(Collectors.toMap(SeatType::getCode, t -> t));
        Set<String> seenRows = new HashSet<>();
        record Planned(String row, int number, int rowIndex, int col, SeatType type, boolean disabled) {}
        List<Planned> planned = new ArrayList<>();
        int rowIndex = 0;
        for (RowInput row : req.rows()) {
            if (!seenRows.add(row.label())) throw new ApiException(ErrorCode.VALIDATION_FAILED, "Duplicate row " + row.label());
            SeatType rowType = Optional.ofNullable(types.get(row.seatType())).orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown seat type " + row.seatType()));
            rowIndex++;
            int col = Math.max(0, row.offset()) + 1;
            Set<Integer> aisles = row.aisleAfter() == null ? Set.of() : new HashSet<>(row.aisleAfter());
            Set<Integer> disabled = row.disabledSeats() == null ? Set.of() : new HashSet<>(row.disabledSeats());
            for (int n = 1; n <= row.seatCount(); n++) {
                String override = row.seatTypeOverrides() == null ? null : row.seatTypeOverrides().get(n);
                SeatType t = override == null ? rowType : Optional.ofNullable(types.get(override))
                        .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown seat type " + override));
                planned.add(new Planned(row.label(), n, rowIndex, col, t, disabled.contains(n)));
                col += aisles.contains(n) ? 2 : 1;
            }
        }

        Map<String, ScreenSeat> existing = screenSeatRepository.findByScreenIdOrderByRowIndexAscColIndexAsc(screenId).stream()
                .collect(Collectors.toMap(ScreenSeat::getLabel, s -> s));
        // Park existing seats on negative coordinates first so re-positioning never trips the unique (row, col) index.
        int parking = -1;
        for (ScreenSeat s : existing.values()) { s.setRowIndex(parking--); s.setColIndex(0); }
        entityManager.flush();

        Set<String> kept = new HashSet<>();
        for (Planned p : planned) {
            String label = p.row() + p.number();
            ScreenSeat s = existing.get(label);
            if (s == null) {
                s = new ScreenSeat();
                s.setScreen(screen);
                s.setLabel(label);
                screen.getSeats().add(s);
            }
            s.setRowLabel(p.row());
            s.setSeatNumber(p.number());
            s.setRowIndex(p.rowIndex());
            s.setColIndex(p.col());
            s.setSeatType(p.type());
            s.setDisabled(p.disabled());
            if (s.getId() == null) screenSeatRepository.save(s);
            kept.add(label);
        }
        int maxRow = rowIndex;
        int spill = 1;
        for (ScreenSeat s : existing.values()) {
            if (kept.contains(s.getLabel())) continue;
            if (screenSeatRepository.isReferenced(s.getId())) {
                s.setDisabled(true);
                s.setRowIndex(1000 + maxRow); // retired seat: kept for history, hidden from layouts
                s.setColIndex(spill++);
            } else {
                screen.getSeats().remove(s);
                screenSeatRepository.delete(s);
            }
        }
        screen.setCapacity((int) planned.stream().filter(p -> !p.disabled()).count());
        entityManager.flush();
        showService.evictScreenCache(screenId);
        audit.record("ADMIN_UPDATED_SEAT_LAYOUT", "SCREEN", screenId, Map.of("seats", planned.size(), "rows", req.rows().size()));
        return screenResponse(screen);
    }

    private AdminScreenResponse screenResponse(CinemaScreen s) {
        List<ScreenSeat> seats = screenSeatRepository.findByScreenIdOrderByRowIndexAscColIndexAsc(s.getId()).stream()
                .filter(x -> x.getRowIndex() > 0 && x.getRowIndex() < 1000).toList();
        Map<String, List<ScreenSeat>> byRow = seats.stream().collect(Collectors.groupingBy(ScreenSeat::getRowLabel, LinkedHashMap::new, Collectors.toList()));
        List<RowInput> layout = new ArrayList<>();
        byRow.forEach((label, rowSeats) -> {
            rowSeats.sort(Comparator.comparingInt(ScreenSeat::getSeatNumber));
            String rowType = rowSeats.stream().collect(Collectors.groupingBy(x -> x.getSeatType().getCode(), Collectors.counting()))
                    .entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("SILVER");
            List<Integer> aisles = new ArrayList<>();
            List<Integer> disabled = new ArrayList<>();
            Map<Integer, String> overrides = new LinkedHashMap<>();
            for (int i = 0; i < rowSeats.size(); i++) {
                ScreenSeat seat = rowSeats.get(i);
                if (i + 1 < rowSeats.size() && rowSeats.get(i + 1).getColIndex() - seat.getColIndex() > 1) aisles.add(seat.getSeatNumber());
                if (seat.isDisabled()) disabled.add(seat.getSeatNumber());
                if (!seat.getSeatType().getCode().equals(rowType)) overrides.put(seat.getSeatNumber(), seat.getSeatType().getCode());
            }
            layout.add(new RowInput(label, rowType, rowSeats.size(), rowSeats.get(0).getColIndex() - 1, aisles, disabled, overrides));
        });
        return new AdminScreenResponse(s.getId(), s.getCinema().getId(), s.getCinema().getName(), s.getName(), s.getScreenType(),
                s.formatList(), s.getCapacity(), s.isActive(), layout, showRepository.existsByScreenIdAndStartTimeAfter(s.getId(), Instant.now(clock)));
    }

    // =================================================================== shows

    @Transactional(readOnly = true)
    public Page<AdminShowResponse> shows(Long cinemaId, Long movieId, LocalDate date, int page, int size) {
        List<Long> scope = access.scopedCinemaIds();
        Specification<Show> spec = (root, q, cb) -> {
            List<jakarta.persistence.criteria.Predicate> ps = new ArrayList<>();
            if (cinemaId != null) ps.add(cb.equal(root.get("cinema").get("id"), cinemaId));
            if (movieId != null) ps.add(cb.equal(root.get("movie").get("id"), movieId));
            if (date != null) ps.add(cb.equal(root.get("showDate"), date));
            if (scope != null) ps.add(scope.isEmpty() ? cb.disjunction() : root.get("cinema").get("id").in(scope));
            return cb.and(ps.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        return showRepository.findAll(spec, PageRequest.of(page, Math.min(size, 200), Sort.by("startTime"))).map(this::showResponse);
    }

    /** Creates a show after validating the screen, formats and that it does not overlap another show on the screen. */
    @Transactional
    public AdminShowResponse createShow(ShowCreateRequest req) {
        CinemaScreen screen = screenRepository.findById(req.screenId()).orElseThrow(() -> ApiException.notFound("Screen"));
        access.requireCinemaAccess(screen.getCinema().getId());
        Movie movie = movieRepository.findById(req.movieId()).filter(Movie::isActive).orElseThrow(() -> ApiException.notFound("Movie"));
        if (!screen.isActive() || !screen.getCinema().isActive()) throw new ApiException(ErrorCode.BAD_REQUEST, "Screen is not active");
        if (screen.getSeats().isEmpty()) throw new ApiException(ErrorCode.BAD_REQUEST, "Configure the seat layout for this screen first");
        if (!screen.formatList().contains(req.format())) throw new ApiException(ErrorCode.BAD_REQUEST, screen.getName() + " does not support " + req.format());
        if (!movie.formatList().contains(req.format())) throw new ApiException(ErrorCode.BAD_REQUEST, movie.getTitle() + " is not released in " + req.format());
        if (!movie.getLanguages().contains(req.language())) throw new ApiException(ErrorCode.BAD_REQUEST, movie.getTitle() + " is not available in " + req.language());

        ZoneId zone = ZoneId.of(props.timezone());
        Instant start = req.date().atTime(req.startTime()).atZone(zone).toInstant();
        Instant end = req.endTime() != null ? endFor(req.date(), req.startTime(), req.endTime(), zone)
                : start.plus(Duration.ofMinutes(movie.getDurationMin() + 20L)); // trailers + interval
        if (!end.isAfter(start)) throw new ApiException(ErrorCode.BAD_REQUEST, "End time must be after start time");
        if (start.isBefore(Instant.now(clock))) throw new ApiException(ErrorCode.BAD_REQUEST, "Shows must start in the future");
        if (showRepository.existsOverlap(screen.getId(), start, end, null)) throw new ApiException(ErrorCode.SHOW_OVERLAP);

        Show show = new Show();
        show.setMovie(movie);
        show.setCinema(screen.getCinema());
        show.setScreen(screen);
        show.setShowDate(req.date());
        show.setStartTime(start);
        show.setEndTime(end);
        show.setFormat(req.format());
        show.setLanguage(req.language());
        show.setBasePrice(req.basePrice());
        showRepository.saveAndFlush(show); // the exclusion constraint is the final guard against races
        showSeatRepository.generateForShow(show.getId(), screen.getId(), req.basePrice());
        audit.record("ADMIN_CREATED_SHOW", "SHOW", show.getId(), Map.of("movieId", movie.getId(), "screenId", screen.getId(), "start", start.toString()));
        return showResponse(show);
    }

    @Transactional
    public AdminShowResponse updateShow(Long id, ShowUpdateRequest req) {
        Show show = showRepository.findById(id).orElseThrow(() -> new ApiException(ErrorCode.SHOW_NOT_FOUND));
        access.requireCinemaAccess(show.getCinema().getId());
        if (req.basePrice() != null && show.getStatus() == ShowStatus.SCHEDULED) {
            show.setBasePrice(req.basePrice());
            showSeatRepository.repriceUnsold(id, req.basePrice()); // sold seats keep the price the customer paid
            audit.record("ADMIN_REPRICED_SHOW", "SHOW", id, Map.of("basePrice", req.basePrice()));
        }
        if ("CANCELLED".equals(req.status()) && show.getStatus() != ShowStatus.CANCELLED) {
            cancelShow(show);
        }
        return showResponse(show);
    }

    private void cancelShow(Show show) {
        Long adminId = com.cineverse.security.SecurityUtils.currentUserIdOrNull();
        for (Booking b : bookingRepository.findByShowIdAndStatusIn(show.getId(), EnumSet.of(BookingStatus.CONFIRMED))) {
            cancellationService.cancel(adminId, b.getId(), "Show cancelled by the cinema", true);
        }
        for (Booking b : bookingRepository.findByShowIdAndStatusIn(show.getId(), EnumSet.of(BookingStatus.PENDING, BookingStatus.PAYMENT_PENDING))) {
            lifecycle.abandon(bookingRepository.findForUpdate(b.getId()).orElseThrow(), BookingStatus.CANCELLED, "Show cancelled by the cinema");
        }
        show.setStatus(ShowStatus.CANCELLED);
        audit.record("ADMIN_CANCELLED_SHOW", "SHOW", show.getId(), null);
    }

    private static Instant endFor(LocalDate date, LocalTime start, LocalTime end, ZoneId zone) {
        LocalDate endDate = end.isBefore(start) ? date.plusDays(1) : date; // shows past midnight
        return endDate.atTime(end).atZone(zone).toInstant();
    }

    private AdminShowResponse showResponse(Show s) {
        return new AdminShowResponse(s.getId(), s.getMovie().getId(), s.getMovie().getTitle(), s.getCinema().getId(), s.getCinema().getName(),
                s.getScreen().getId(), s.getScreen().getName(), s.getShowDate(), s.getStartTime(), s.getEndTime(), s.getFormat(), s.getLanguage(),
                s.getBasePrice(), s.getStatus().name(), showSeatRepository.countByShowIdAndStatus(s.getId(), SeatStatus.BOOKED),
                showSeatRepository.countByShowId(s.getId()) - showSeatRepository.countByShowIdAndStatus(s.getId(), SeatStatus.BLOCKED));
    }

    // =================================================================== events

    @Transactional(readOnly = true)
    public Page<AdminEventResponse> events(int page, int size) {
        return eventRepository.adminList(PageRequest.of(page, Math.min(size, 100))).map(this::eventResponse);
    }

    @Transactional(readOnly = true)
    public AdminEventResponse event(Long id) {
        return eventResponse(eventRepository.findById(id).orElseThrow(() -> ApiException.notFound("Event")));
    }

    @Transactional
    public AdminEventResponse createEvent(EventUpsertRequest req) {
        access.requireAdmin();
        Event e = new Event();
        e.setSlug(Slugs.unique(req.title(), eventRepository::existsBySlug));
        applyEvent(e, req);
        eventRepository.save(e);
        audit.record("ADMIN_CREATED_EVENT", "EVENT", e.getId(), Map.of("title", e.getTitle()));
        return eventResponse(e);
    }

    @Transactional
    public AdminEventResponse updateEvent(Long id, EventUpsertRequest req) {
        access.requireAdmin();
        Event e = eventRepository.findById(id).orElseThrow(() -> ApiException.notFound("Event"));
        applyEvent(e, req);
        audit.record("ADMIN_UPDATED_EVENT", "EVENT", id, null);
        return eventResponse(e);
    }

    private void applyEvent(Event e, EventUpsertRequest req) {
        EventCategory cat = eventCategoryRepository.findByCode(req.categoryCode()).orElseThrow(() -> ApiException.notFound("Event category"));
        Venue venue = venueRepository.findById(req.venueId()).orElseThrow(() -> ApiException.notFound("Venue"));
        e.setTitle(req.title().trim());
        e.setDescription(req.description().trim());
        e.setCategory(cat);
        e.setVertical(cat.getVertical());
        e.setVenue(venue);
        e.setCity(venue.getCity());
        e.setArtist(blankToNull(req.artist()));
        e.setTeamHome(blankToNull(req.teamHome()));
        e.setTeamAway(blankToNull(req.teamAway()));
        e.setLanguages(blankToNull(req.languages()));
        e.setDurationMin(req.durationMin());
        e.setAgeRestriction(blankToNull(req.ageRestriction()));
        e.setPosterUrl(blankToNull(req.posterUrl()));
        e.setBannerUrl(blankToNull(req.bannerUrl()));
        e.setFeatured(req.featured());
        e.setStatus(req.status() == null ? EventStatus.PUBLISHED : EventStatus.valueOf(req.status()));

        Map<Long, EventSession> current = e.getSessions().stream().filter(s -> s.getId() != null)
                .collect(Collectors.toMap(EventSession::getId, s -> s));
        Set<Long> keep = new HashSet<>();
        for (SessionInput si : req.sessions()) {
            if (!si.endTime().isAfter(si.startTime())) throw new ApiException(ErrorCode.VALIDATION_FAILED, "Session end must be after start");
            EventSession s = si.id() == null ? null : current.get(si.id());
            if (s == null) {
                s = new EventSession();
                s.setEvent(e);
                e.getSessions().add(s);
            } else {
                keep.add(s.getId());
            }
            s.setStartTime(si.startTime());
            s.setEndTime(si.endTime());
            Map<Long, EventTicketCategory> cats = s.getTicketCategories().stream().filter(c -> c.getId() != null)
                    .collect(Collectors.toMap(EventTicketCategory::getId, c -> c));
            Set<Long> keepCats = new HashSet<>();
            int order = 1;
            for (CategoryInput ci : si.categories()) {
                EventTicketCategory c = ci.id() == null ? null : cats.get(ci.id());
                if (c == null) {
                    c = new EventTicketCategory();
                    c.setSession(s);
                    s.getTicketCategories().add(c);
                } else {
                    keepCats.add(c.getId());
                    if (ci.capacity() < c.getSold() + c.getHeld()) {
                        throw new ApiException(ErrorCode.CONFLICT, c.getName() + ": capacity cannot be lower than tickets already sold (" + (c.getSold() + c.getHeld()) + ")");
                    }
                }
                c.setName(ci.name().trim());
                c.setDescription(blankToNull(ci.description()));
                c.setPrice(ci.price());
                c.setCapacity(ci.capacity());
                c.setMaxPerBooking(ci.maxPerBooking());
                c.setSortOrder(order++);
            }
            for (EventTicketCategory c : new ArrayList<>(s.getTicketCategories())) {
                if (c.getId() != null && !keepCats.contains(c.getId())) {
                    if (c.getSold() + c.getHeld() > 0) throw new ApiException(ErrorCode.CONFLICT, c.getName() + " has bookings and cannot be removed");
                    s.getTicketCategories().remove(c);
                }
            }
        }
        for (EventSession s : new ArrayList<>(e.getSessions())) {
            if (s.getId() != null && !keep.contains(s.getId())) {
                boolean sold = s.getTicketCategories().stream().anyMatch(c -> c.getSold() + c.getHeld() > 0);
                if (sold) throw new ApiException(ErrorCode.CONFLICT, "A session with bookings cannot be removed; cancel the event instead");
                e.getSessions().remove(s);
            }
        }
    }

    private AdminEventResponse eventResponse(Event e) {
        return new AdminEventResponse(e.getId(), e.getTitle(), e.getSlug(), e.getDescription(), e.getCategory().getCode(), e.getVertical().name(),
                e.getVenue().getId(), e.getVenue().getName(), e.getCity().getName(), e.getArtist(), e.getTeamHome(), e.getTeamAway(),
                e.getLanguages(), e.getDurationMin(), e.getAgeRestriction(), e.getPosterUrl(), e.getBannerUrl(), e.isFeatured(), e.getStatus().name(),
                e.getSessions().stream().map(s -> new AdminSession(s.getId(), s.getStartTime(), s.getEndTime(),
                        s.getTicketCategories().stream().map(c -> new AdminCategory(c.getId(), c.getName(), c.getDescription(), c.getPrice(),
                                c.getCapacity(), c.getSold(), c.getHeld(), c.getMaxPerBooking())).toList())).toList());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
