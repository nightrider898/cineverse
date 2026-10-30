package com.cineverse.service;

import com.cineverse.config.AppProperties;
import com.cineverse.dto.CatalogDtos.*;
import com.cineverse.dto.ShowDtos.*;
import com.cineverse.entity.*;
import com.cineverse.entity.enums.SeatLockStatus;
import com.cineverse.entity.enums.SeatStatus;
import com.cineverse.entity.enums.ShowStatus;
import com.cineverse.exception.ApiException;
import com.cineverse.exception.ErrorCode;
import com.cineverse.mapper.CatalogMapper;
import com.cineverse.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Read side of showtimes and seat maps. Seat availability is always read live from PostgreSQL:
 * a LOCKED seat whose hold has lapsed is reported AVAILABLE even before the cleanup job runs.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShowService {
    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;
    private final SeatLockRepository seatLockRepository;
    private final SeatTypeRepository seatTypeRepository;
    private final MovieRepository movieRepository;
    private final CinemaRepository cinemaRepository;
    private final CatalogService catalogService;
    private final CatalogMapper mapper;
    private final AppProperties props;
    private final Clock clock;

    public ShowResponse get(Long showId, Long userId) {
        Instant now = Instant.now(clock);
        Show show = showRepository.findById(showId).orElseThrow(() -> new ApiException(ErrorCode.SHOW_NOT_FOUND));
        List<ShowSeat> seats = showSeatRepository.findSeatMap(showId);
        Map<UUID, SeatLock> live = liveLocks(seats, now);

        List<SeatResponse> seatResponses = new ArrayList<>();
        long available = 0;
        for (ShowSeat s : seats) {
            String status = displayStatus(s, live);
            boolean mine = userId != null && s.getLockId() != null && live.containsKey(s.getLockId())
                    && live.get(s.getLockId()).getUserId().equals(userId) && s.getStatus() == SeatStatus.LOCKED;
            if ("AVAILABLE".equals(status)) available++;
            ScreenSeat ss = s.getScreenSeat();
            seatResponses.add(new SeatResponse(s.getId(), s.getLabel(), ss.getRowLabel(), ss.getSeatNumber(), ss.getRowIndex(), ss.getColIndex(),
                    s.getSeatTypeCode(), s.getPrice(), mine ? "LOCKED" : status, mine));
        }
        int rows = seats.stream().mapToInt(x -> x.getScreenSeat().getRowIndex()).max().orElse(0);
        int cols = seats.stream().mapToInt(x -> x.getScreenSeat().getColIndex()).max().orElse(0);
        List<String> rowLabels = seats.stream().map(x -> x.getScreenSeat().getRowLabel()).distinct().toList();

        ActiveLock yourLock = null;
        if (userId != null) {
            yourLock = seatLockRepository.findActiveForUserAndShow(userId, showId).stream()
                    .filter(l -> l.isUsable(now)).findFirst()
                    .map(l -> new ActiveLock(l.getId(), l.labels(), l.getExpiresAt())).orElse(null);
        }
        boolean bookable = show.getStatus() == ShowStatus.SCHEDULED && show.getStartTime().isAfter(now);
        return new ShowResponse(show.getId(), mapper.movie(show.getMovie()), mapper.cinema(show.getCinema()), mapper.screen(show.getScreen()),
                show.getShowDate(), show.getStartTime(), show.getEndTime(), show.getFormat(), show.getLanguage(), pricing(show, seats),
                new SeatLayout(rows, cols, rowLabels, seatResponses), available, seats.stream().filter(x -> x.getStatus() != SeatStatus.BLOCKED).count(),
                bookable, yourLock, now, props.seatLock().maxSeats());
    }

    /** Lightweight poll for the seat map: statuses only. */
    public SeatAvailabilityResponse availability(Long showId, Long userId) {
        Instant now = Instant.now(clock);
        if (!showRepository.existsById(showId)) throw new ApiException(ErrorCode.SHOW_NOT_FOUND);
        List<ShowSeat> seats = showSeatRepository.findSeatMap(showId);
        Map<UUID, SeatLock> live = liveLocks(seats, now);
        List<SeatStatusItem> items = seats.stream().map(s -> {
            boolean mine = userId != null && s.getStatus() == SeatStatus.LOCKED && live.containsKey(s.getLockId())
                    && live.get(s.getLockId()).getUserId().equals(userId);
            return new SeatStatusItem(s.getLabel(), mine ? "LOCKED" : displayStatus(s, live), mine);
        }).toList();
        return new SeatAvailabilityResponse(showId, items, now);
    }

    public MovieShowtimesResponse showtimesForMovie(Long movieId, String citySlug, LocalDate date, Double lat, Double lng,
                                                    String language, String format) {
        Instant now = Instant.now(clock);
        Movie movie = movieRepository.findById(movieId).orElseThrow(() -> ApiException.notFound("Movie"));
        City city = Optional.ofNullable(catalogService.resolveCity(citySlug)).orElseThrow(() -> new ApiException(ErrorCode.BAD_REQUEST, "city is required"));
        List<LocalDate> dates = showRepository.availableDates(movieId, city.getId(), now);
        LocalDate day = date != null ? date : dates.isEmpty() ? LocalDate.now(clock.withZone(zone())) : dates.get(0);

        List<Show> shows = showRepository.findForMovieInCity(movieId, city.getId(), day, now);
        List<String> languages = shows.stream().map(Show::getLanguage).distinct().sorted().toList();
        List<String> formats = shows.stream().map(Show::getFormat).distinct().sorted().toList();
        shows = shows.stream()
                .filter(s -> language == null || language.isBlank() || s.getLanguage().equalsIgnoreCase(language))
                .filter(s -> format == null || format.isBlank() || s.getFormat().equalsIgnoreCase(format)).toList();

        Map<Long, long[]> counts = counts(shows);
        Map<Long, List<SeatPrice>> prices = priceTable(shows);
        Map<Cinema, List<Show>> byCinema = shows.stream().collect(Collectors.groupingBy(Show::getCinema, LinkedHashMap::new, Collectors.toList()));
        List<CinemaShowtimes> cinemas = byCinema.entrySet().stream().map(e -> {
            Cinema c = e.getKey();
            return new CinemaShowtimes(mapper.cinema(c, CatalogMapper.distanceKm(lat, lng, c.getLatitude(), c.getLongitude())),
                    e.getValue().stream().map(s -> slot(s, counts.get(s.getId()), prices.get(s.getId()))).toList());
        }).sorted(Comparator.comparing((CinemaShowtimes cs) -> cs.cinema().distanceKm() == null ? Double.MAX_VALUE : cs.cinema().distanceKm())
                .thenComparing(cs -> cs.cinema().name())).toList();
        return new MovieShowtimesResponse(mapper.movie(movie), day, dates, languages, formats, cinemas);
    }

    public CinemaShowtimesResponse showtimesForCinema(Long cinemaId, LocalDate date) {
        Instant now = Instant.now(clock);
        Cinema cinema = cinemaRepository.findById(cinemaId).filter(Cinema::isActive).orElseThrow(() -> ApiException.notFound("Cinema"));
        LocalDate day = date != null ? date : LocalDate.now(clock.withZone(zone()));
        List<Show> shows = showRepository.findForCinema(cinemaId, day, now);
        Map<Long, long[]> counts = counts(shows);
        Map<Long, List<SeatPrice>> prices = priceTable(shows);
        Map<Movie, List<Show>> byMovie = shows.stream().collect(Collectors.groupingBy(Show::getMovie, LinkedHashMap::new, Collectors.toList()));
        List<MovieShowtimes> movies = byMovie.entrySet().stream().map(e -> new MovieShowtimes(mapper.movie(e.getKey()),
                e.getValue().stream().map(s -> slot(s, counts.get(s.getId()), prices.get(s.getId()))).toList())).toList();
        return new CinemaShowtimesResponse(mapper.cinema(cinema), day, movies);
    }

    private ShowtimeSlot slot(Show s, long[] count, List<SeatPrice> prices) {
        long availableSeats = count == null ? 0 : count[0];
        long total = count == null ? 0 : count[1];
        double ratio = total == 0 ? 0 : (double) availableSeats / total;
        String availability = availableSeats == 0 ? "SOLD_OUT" : ratio < 0.15 ? "ALMOST_FULL" : ratio < 0.45 ? "FILLING_FAST" : "AVAILABLE";
        BigDecimal min = prices.stream().map(SeatPrice::price).min(BigDecimal::compareTo).orElse(s.getBasePrice());
        BigDecimal max = prices.stream().map(SeatPrice::price).max(BigDecimal::compareTo).orElse(s.getBasePrice());
        return new ShowtimeSlot(s.getId(), s.getStartTime(), s.getEndTime(), s.getFormat(), s.getLanguage(), s.getScreen().getName(),
                min, max, prices, availability, availableSeats, total);
    }

    /** [available, sellable total] per show, counted in one grouped query. */
    private Map<Long, long[]> counts(List<Show> shows) {
        Map<Long, long[]> out = new HashMap<>();
        if (shows.isEmpty()) return out;
        for (Object[] row : showSeatRepository.statusCounts(shows.stream().map(Show::getId).toList())) {
            long[] c = out.computeIfAbsent((Long) row[0], k -> new long[2]);
            SeatStatus st = (SeatStatus) row[1];
            long n = (Long) row[2];
            if (st == SeatStatus.AVAILABLE) c[0] += n;
            if (st != SeatStatus.BLOCKED) c[1] += n;
        }
        return out;
    }

    private Map<Long, List<SeatPrice>> priceTable(List<Show> shows) {
        List<SeatType> types = seatTypeRepository.findAllByOrderBySortOrderAsc();
        Map<Long, List<SeatPrice>> out = new HashMap<>();
        for (Show s : shows) {
            Set<String> present = screenSeatTypes(s.getScreen());
            out.put(s.getId(), types.stream().filter(t -> present.contains(t.getCode()))
                    .map(t -> new SeatPrice(t.getCode(), t.getName(), s.getBasePrice().add(t.getPriceDelta()))).toList());
        }
        return out;
    }

    private final Map<Long, Set<String>> screenTypeCache = new java.util.concurrent.ConcurrentHashMap<>();

    private Set<String> screenSeatTypes(CinemaScreen screen) {
        return screenTypeCache.computeIfAbsent(screen.getId(), id -> screen.getSeats().stream()
                .map(ss -> ss.getSeatType().getCode()).collect(Collectors.toSet()));
    }

    public void evictScreenCache(Long screenId) {
        screenTypeCache.remove(screenId);
    }

    private List<SeatPrice> pricing(Show show, List<ShowSeat> seats) {
        Map<String, BigDecimal> byType = new LinkedHashMap<>();
        seats.forEach(s -> byType.putIfAbsent(s.getSeatTypeCode(), s.getPrice()));
        return seatTypeRepository.findAllByOrderBySortOrderAsc().stream().filter(t -> byType.containsKey(t.getCode()))
                .map(t -> new SeatPrice(t.getCode(), t.getName(), byType.get(t.getCode()))).toList();
    }

    private Map<UUID, SeatLock> liveLocks(List<ShowSeat> seats, Instant now) {
        Set<UUID> ids = seats.stream().filter(s -> s.getStatus() == SeatStatus.LOCKED && s.getLockId() != null)
                .map(ShowSeat::getLockId).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        return seatLockRepository.findAllById(ids).stream()
                .filter(l -> l.getStatus() == SeatLockStatus.ACTIVE && l.getExpiresAt().isAfter(now))
                .collect(Collectors.toMap(SeatLock::getId, l -> l));
    }

    private static String displayStatus(ShowSeat s, Map<UUID, SeatLock> live) {
        return switch (s.getStatus()) {
            case BOOKED -> "BOOKED";
            case BLOCKED -> "DISABLED";
            case LOCKED -> live.containsKey(s.getLockId()) ? "LOCKED" : "AVAILABLE";
            case AVAILABLE -> "AVAILABLE";
        };
    }

    private ZoneId zone() {
        return ZoneId.of(props.timezone());
    }
}
