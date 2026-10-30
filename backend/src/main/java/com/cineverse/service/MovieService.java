package com.cineverse.service;

import com.cineverse.config.CacheConfig;
import com.cineverse.dto.CatalogDtos.*;
import com.cineverse.entity.*;
import com.cineverse.entity.enums.BookingStatus;
import com.cineverse.entity.enums.MovieStatus;
import com.cineverse.exception.ApiException;
import com.cineverse.mapper.CatalogMapper;
import com.cineverse.repository.*;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MovieService {
    private final MovieRepository movieRepository;
    private final RatingRepository ratingRepository;
    private final ShowRepository showRepository;
    private final BookingRepository bookingRepository;
    private final CatalogService catalogService;
    private final CatalogMapper mapper;
    private final Clock clock;

    public record MovieFilter(String city, String language, String genre, String format, String status,
                              BigDecimal minRating, LocalDate date, Long cinemaId, String q) {}

    public Page<MovieResponse> list(MovieFilter f, int page, int size, String sort) {
        Specification<Movie> spec = (root, query, cb) -> cb.isTrue(root.get("active"));
        if (f.status() != null && !f.status().isBlank()) {
            MovieStatus st = MovieStatus.valueOf(f.status().toUpperCase());
            spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), st));
        }
        if (f.language() != null && !f.language().isBlank()) {
            String lang = f.language().trim().toLowerCase();
            spec = spec.and((root, q, cb) -> {
                Subquery<Long> sq = q.subquery(Long.class);
                Root<Movie> m2 = sq.correlate(root);
                Join<Movie, String> langs = m2.join("languages");
                sq.select(m2.get("id")).where(cb.equal(cb.lower(langs), lang));
                return cb.exists(sq);
            });
        }
        if (f.genre() != null && !f.genre().isBlank()) {
            String g = f.genre().trim().toLowerCase();
            spec = spec.and((root, q, cb) -> {
                Subquery<Long> sq = q.subquery(Long.class);
                Root<Movie> m2 = sq.correlate(root);
                Join<Movie, Genre> genres = m2.join("genres");
                sq.select(m2.get("id")).where(cb.or(cb.equal(cb.lower(genres.get("name")), g), cb.equal(genres.get("slug"), g)));
                return cb.exists(sq);
            });
        }
        if (f.format() != null && !f.format().isBlank()) {
            String fmt = "%" + f.format().trim().toUpperCase() + "%";
            spec = spec.and((root, q, cb) -> cb.like(cb.upper(root.get("formats")), fmt));
        }
        if (f.q() != null && !f.q().isBlank()) {
            String like = "%" + f.q().trim().toLowerCase() + "%";
            spec = spec.and((root, q, cb) -> cb.like(cb.lower(root.get("title")), like));
        }
        if (f.minRating() != null) {
            spec = spec.and((root, q, cb) -> {
                Subquery<Long> sq = q.subquery(Long.class);
                Root<Rating> r = sq.from(Rating.class);
                sq.select(r.get("movieId")).where(cb.equal(r.get("movieId"), root.get("id")), cb.ge(r.get("average"), f.minRating()));
                return cb.exists(sq);
            });
        }
        City city = catalogService.resolveCity(f.city());
        if (city != null || f.date() != null || f.cinemaId() != null) {
            Instant now = Instant.now(clock);
            spec = spec.and((root, q, cb) -> {
                Subquery<Long> sq = q.subquery(Long.class);
                Root<Show> s = sq.from(Show.class);
                List<Predicate> ps = new ArrayList<>();
                ps.add(cb.equal(s.get("movie").get("id"), root.get("id")));
                ps.add(cb.greaterThan(s.get("startTime"), now));
                ps.add(cb.equal(s.get("status"), com.cineverse.entity.enums.ShowStatus.SCHEDULED));
                if (city != null) ps.add(cb.equal(s.get("cinema").get("city").get("id"), city.getId()));
                if (f.date() != null) ps.add(cb.equal(s.get("showDate"), f.date()));
                if (f.cinemaId() != null) ps.add(cb.equal(s.get("cinema").get("id"), f.cinemaId()));
                sq.select(s.get("id")).where(ps.toArray(Predicate[]::new));
                Predicate hasShows = cb.exists(sq);
                // Coming-soon titles are shown regardless of city (no shows yet) unless a date/cinema is requested.
                if (f.date() == null && f.cinemaId() == null) {
                    return cb.or(hasShows, cb.equal(root.get("status"), MovieStatus.COMING_SOON));
                }
                return hasShows;
            });
        }

        Sort s = switch (sort == null ? "" : sort.toLowerCase()) {
            // our own reviews first, then the IMDb figures supplied with the catalog
            case "rating,desc", "rating" -> Sort.by(Sort.Order.desc("rating.average"), Sort.Order.desc("imdbRating"), Sort.Order.asc("title"));
            case "popularity", "votes,desc" -> Sort.by(Sort.Order.desc("rating.totalVotes"), Sort.Order.desc("imdbVotes"), Sort.Order.asc("title"));
            case "title", "title,asc" -> Sort.by("title");
            case "releasedate,asc" -> Sort.by("releaseDate");
            default -> Sort.by(Sort.Order.desc("releaseDate"), Sort.Order.asc("title"));
        };
        Page<Movie> result = movieRepository.findAll(spec, PageRequest.of(page, Math.min(size, 50), s));
        List<MovieResponse> cards = mapper.movies(result.getContent());
        return new PageImpl<>(cards, result.getPageable(), result.getTotalElements());
    }

    public MovieHomeResponse home(String citySlug, Long userId) {
        Instant now = Instant.now(clock);
        City city = catalogService.resolveCity(citySlug);
        List<Movie> nowShowing = movieRepository.findByActiveTrueAndStatusOrderByReleaseDateAsc(MovieStatus.NOW_SHOWING);
        if (city != null) {
            Set<Long> showing = new HashSet<>(showRepository.movieIdsShowingInCity(city.getId(), now));
            nowShowing = nowShowing.stream().filter(m -> showing.contains(m.getId())).toList();
        }
        List<MovieResponse> nowCards = mapper.movies(nowShowing);
        Map<Long, MovieResponse> byId = nowCards.stream().collect(Collectors.toMap(MovieResponse::id, m -> m, (a, b) -> a, LinkedHashMap::new));

        List<MovieResponse> featured = nowCards.stream().filter(MovieResponse::featured).toList();
        if (featured.isEmpty()) featured = nowCards.stream().limit(3).toList();

        // Our own reviews once a title has them; until then the IMDb figures supplied with the catalog.
        List<MovieResponse> topRated = nowCards.stream().sorted(Comparator.comparingDouble(MovieService::score).reversed()).toList();
        List<MovieResponse> popular = nowCards.stream().sorted(Comparator.comparingLong(MovieService::popularity).reversed()).toList();

        List<MovieResponse> trending = movieRepository.trendingMovieIds(now.minus(Duration.ofDays(7)), PageRequest.of(0, 10))
                .stream().map(byId::get).filter(Objects::nonNull).collect(Collectors.toCollection(ArrayList::new));
        for (MovieResponse m : popular) {
            if (trending.size() >= 8) break;
            if (!trending.contains(m)) trending.add(m);
        }

        List<MovieResponse> recommended = recommended(userId, nowCards, topRated);
        List<MovieResponse> comingSoon = mapper.movies(movieRepository.findByActiveTrueAndStatusOrderByReleaseDateAsc(MovieStatus.COMING_SOON));
        List<MovieResponse> classics = mapper.movies(movieRepository.findByActiveTrueAndStatusOrderByReleaseDateAsc(MovieStatus.CLASSIC)).stream()
                .sorted(Comparator.comparingLong(MovieService::popularity).reversed()).limit(20).toList();
        return new MovieHomeResponse(featured, nowCards, comingSoon, trending, topRated, popular, recommended, classics);
    }

    /** Rating on a 10-point scale: our reviews (5 stars x2) when present, else the catalog's IMDb rating. */
    static double score(MovieResponse m) {
        if (m.votes() > 0 && m.rating() != null) return m.rating().doubleValue() * 2;
        return m.imdbRating() == null ? 0 : m.imdbRating().doubleValue();
    }

    static long popularity(MovieResponse m) {
        return m.votes() > 0 ? m.votes() : (m.imdbVotes() == null ? 0 : m.imdbVotes());
    }

    /** Genre affinity from the customer's own confirmed bookings, falling back to top rated. */
    private List<MovieResponse> recommended(Long userId, List<MovieResponse> nowCards, List<MovieResponse> topRated) {
        if (userId == null) return topRated.stream().limit(8).toList();
        Page<Booking> past = bookingRepository.findByUserIdAndStatusInOrderByUpdatedAtDesc(userId,
                EnumSet.of(BookingStatus.CONFIRMED), PageRequest.of(0, 20));
        Map<String, Long> genreScore = new HashMap<>();
        Set<Long> seen = new HashSet<>();
        for (Booking b : past) {
            if (b.getShow() == null) continue;
            seen.add(b.getShow().getMovie().getId());
            b.getShow().getMovie().getGenres().forEach(g -> genreScore.merge(g.getName(), 1L, Long::sum));
        }
        if (genreScore.isEmpty()) return topRated.stream().limit(8).toList();
        return nowCards.stream().filter(m -> !seen.contains(m.id()))
                .sorted(Comparator.comparingLong((MovieResponse m) -> m.genres().stream().mapToLong(g -> genreScore.getOrDefault(g, 0L)).sum())
                        .reversed().thenComparing(MovieResponse::rating, Comparator.reverseOrder()))
                .limit(8).toList();
    }

    @Cacheable(CacheConfig.MOVIE_DETAIL)
    public MovieDetailResponse detail(Long id) {
        Movie m = movieRepository.findById(id).filter(Movie::isActive).orElseThrow(() -> ApiException.notFound("Movie"));
        Rating r = ratingRepository.findById(id).orElse(null);
        List<CastMemberResponse> cast = m.getCast().stream().filter(c -> c.getCrewRole() == null)
                .map(c -> new CastMemberResponse(c.getActor().getId(), c.getActor().getName(), c.getActor().getPhotoUrl(), c.getCharacterName(), "Actor")).toList();
        List<CastMemberResponse> crew = new ArrayList<>();
        m.getDirectors().forEach(d -> crew.add(new CastMemberResponse(d.getId(), d.getName(), d.getPhotoUrl(), null, "Director")));
        m.getCast().stream().filter(c -> c.getCrewRole() != null).forEach(c ->
                crew.add(new CastMemberResponse(c.getActor().getId(), c.getActor().getName(), c.getActor().getPhotoUrl(), null, c.getCrewRole())));
        return new MovieDetailResponse(mapper.movie(m, r), m.getSynopsis(),
                m.getDirectors().stream().map(d -> new PersonResponse(d.getId(), d.getName(), d.getPhotoUrl())).toList(),
                cast, crew, m.getImages().stream().map(MovieImage::getUrl).toList(), mapper.ratingSummary(r));
    }

    public MovieDetailResponse detailBySlug(String slug) {
        return detail(movieRepository.findBySlug(slug).orElseThrow(() -> ApiException.notFound("Movie")).getId());
    }

    public List<MovieResponse> similar(Long id) {
        Movie m = movieRepository.findById(id).orElseThrow(() -> ApiException.notFound("Movie"));
        List<Long> genreIds = m.getGenres().stream().map(Genre::getId).toList();
        if (genreIds.isEmpty()) return List.of();
        return mapper.movies(movieRepository.similar(id, genreIds, PageRequest.of(0, 8)));
    }
}
