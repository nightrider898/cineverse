package com.cineverse.media;

import com.cineverse.config.AppProperties;
import com.cineverse.config.CacheConfig;
import com.cineverse.entity.*;
import com.cineverse.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fills in posters, backdrops, official YouTube trailers and missing details (runtime, release date, synopsis,
 * genres, director, cast photos) from TMDB, with an optional YouTube search fallback for trailers.
 *
 * Network calls run outside any transaction; each movie is then updated in its own short transaction.
 * Admin-edited media (movies.media_locked) is never overwritten.
 */
@Slf4j
@Service
public class MediaSyncService {
    public static final List<String> PLACEHOLDER_SYNOPSES = List.of(
            "Story details will be announced soon.",
            "A celebrated classic, back on the big screen for a limited re-release.");
    private static final String DEMO_TRAILER_ID = "aqz-KE-bpKQ";

    private static final Map<String, String> LANGUAGE_CODES = Map.ofEntries(
            Map.entry("telugu", "te"), Map.entry("hindi", "hi"), Map.entry("tamil", "ta"), Map.entry("malayalam", "ml"),
            Map.entry("kannada", "kn"), Map.entry("english", "en"), Map.entry("japanese", "ja"), Map.entry("korean", "ko"),
            Map.entry("french", "fr"), Map.entry("italian", "it"), Map.entry("german", "de"), Map.entry("spanish", "es"),
            Map.entry("portuguese", "pt"), Map.entry("persian", "fa"), Map.entry("swedish", "sv"), Map.entry("danish", "da"),
            Map.entry("russian", "ru"), Map.entry("turkish", "tr"), Map.entry("arabic", "ar"), Map.entry("bengali", "bn"),
            Map.entry("marathi", "mr"), Map.entry("punjabi", "pa"));

    /** TMDB genre names -> our genre slugs. */
    private static final Map<String, String> GENRES = Map.ofEntries(
            Map.entry("Action", "action"), Map.entry("Adventure", "adventure"), Map.entry("Animation", "animation"),
            Map.entry("Comedy", "comedy"), Map.entry("Crime", "thriller"), Map.entry("Drama", "drama"),
            Map.entry("Family", "family"), Map.entry("Fantasy", "adventure"), Map.entry("History", "historical"),
            Map.entry("Horror", "horror"), Map.entry("Mystery", "mystery"), Map.entry("Romance", "romance"),
            Map.entry("Science Fiction", "sci-fi"), Map.entry("Thriller", "thriller"), Map.entry("War", "action"),
            Map.entry("Western", "action"), Map.entry("Music", "drama"), Map.entry("Documentary", "biography"));

    public record SyncResult(Long movieId, String title, boolean matched, Integer tmdbId, boolean poster, boolean trailer,
                             String trailerSource, String note) {}

    public record BatchSummary(int processed, int matched, int posters, int trailers, int failed, boolean tmdbConfigured,
                               boolean youtubeConfigured, String message) {}

    private final MovieRepository movieRepository;
    private final GenreRepository genreRepository;
    private final DirectorRepository directorRepository;
    private final ActorRepository actorRepository;
    private final TmdbClient tmdb;
    private final YouTubeSearchClient youtube;
    private final AppProperties props;
    private final TransactionTemplate tx;
    private final CacheManager cacheManager;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public MediaSyncService(MovieRepository movieRepository, GenreRepository genreRepository, DirectorRepository directorRepository,
                            ActorRepository actorRepository, TmdbClient tmdb, YouTubeSearchClient youtube, AppProperties props,
                            PlatformTransactionManager txm, CacheManager cacheManager, Clock clock) {
        this.movieRepository = movieRepository;
        this.genreRepository = genreRepository;
        this.directorRepository = directorRepository;
        this.actorRepository = actorRepository;
        this.tmdb = tmdb;
        this.youtube = youtube;
        this.props = props;
        this.tx = new TransactionTemplate(txm);
        this.cacheManager = cacheManager;
        this.clock = clock;
    }

    public boolean isRunning() {
        return running.get();
    }

    /** Syncs every movie that was never synced (or not within cineverse.media.resync-after). Single-flight. */
    public BatchSummary syncPending(boolean force) {
        if (!tmdb.enabled()) {
            return new BatchSummary(0, 0, 0, 0, 0, false, youtube.enabled(),
                    "TMDB_API_KEY is not set, so posters and trailers cannot be fetched. Add it to .env and restart.");
        }
        if (!running.compareAndSet(false, true)) {
            return new BatchSummary(0, 0, 0, 0, 0, true, youtube.enabled(), "A media sync is already running.");
        }
        int processed = 0, matched = 0, posters = 0, trailers = 0, failed = 0;
        try {
            Instant before = force ? Instant.now(clock).plusSeconds(1) : Instant.now(clock).minus(props.media().resyncAfter());
            List<Long> ids = movieRepository.idsNeedingMediaSync(before);
            log.info("media_sync_started movies={}", ids.size());
            for (Long id : ids) {
                try {
                    SyncResult r = syncMovie(id);
                    processed++;
                    if (r.matched()) matched++;
                    if (r.poster()) posters++;
                    if (r.trailer()) trailers++;
                } catch (TmdbClient.MediaSyncException e) {
                    failed++;
                    log.warn("media_sync_error movieId={} reason={}", id, e.getMessage());
                    if (e.getMessage().contains("401") || e.getMessage().contains("Could not reach")) {
                        return new BatchSummary(processed, matched, posters, trailers, failed, true, youtube.enabled(), e.getMessage());
                    }
                }
                sleepQuietly(60); // stay well inside TMDB's rate limit
            }
            String msg = "Synced " + processed + " movies: " + matched + " matched on TMDB, " + posters + " posters, " + trailers + " trailers.";
            log.info("media_sync_finished {}", msg);
            return new BatchSummary(processed, matched, posters, trailers, failed, true, youtube.enabled(), msg);
        } finally {
            running.set(false);
            evictMovieCache();
        }
    }

    /** Fetches and applies media for one movie. Network first, then one short transaction. */
    public SyncResult syncMovie(Long movieId) {
        Snapshot s = tx.execute(st -> snapshot(movieRepository.findById(movieId).orElseThrow()));
        if (s == null) throw new IllegalStateException("movie " + movieId);
        if (!tmdb.enabled()) {
            return new SyncResult(movieId, s.title, false, s.tmdbId, false, false, null, "TMDB_API_KEY is not configured");
        }
        Integer tmdbId = s.tmdbId != null ? s.tmdbId : findTmdbId(s);
        JsonNode details = tmdbId == null ? null : tmdb.movieDetails(tmdbId);
        if (details != null && details.path("id").isMissingNode()) details = null;

        String trailerKey = details == null ? null : pickTrailer(details.path("videos").path("results"), s.languageCode);
        String trailerSource = trailerKey != null ? "TMDB" : null;
        if (trailerKey == null && !s.mediaLocked && !s.hasRealTrailer && youtube.enabled()) {
            trailerKey = youtube.findTrailer(s.title, s.year, s.languageCode);
            if (trailerKey != null) trailerSource = "YOUTUBE_SEARCH";
        }
        final JsonNode d = details;
        final Integer finalTmdbId = details == null ? null : tmdbId;
        final String key = trailerKey;
        final String source = trailerSource;
        SyncResult result = tx.execute(st -> apply(movieId, finalTmdbId, d, key, source));
        evictMovieCache();
        return result;
    }

    // ------------------------------------------------------------------------------------------- matching

    private Integer findTmdbId(Snapshot s) {
        Integer best = bestMatch(tmdb.searchMovie(s.title, s.year), s);
        if (best == null && s.year != null) best = bestMatch(tmdb.searchMovie(s.title, null), s);
        return best;
    }

    private Integer bestMatch(JsonNode search, Snapshot s) {
        String want = normaliseTitle(s.title);
        int bestScore = 0;
        Integer bestId = null;
        double bestPopularity = -1;
        for (JsonNode r : search.path("results")) {
            int score = 0;
            String t = normaliseTitle(r.path("title").asText(""));
            String o = normaliseTitle(r.path("original_title").asText(""));
            if (t.equals(want) || o.equals(want)) score += 4;
            else if (!want.isEmpty() && (t.contains(want) || want.contains(t) && !t.isEmpty())) score += 2;
            String date = r.path("release_date").asText("");
            if (s.year != null && date.matches("\\d{4}.*")) {
                int y = Integer.parseInt(date.substring(0, 4));
                if (y == s.year) score += 3;
                else if (Math.abs(y - s.year) == 1) score += 1;
            }
            if (s.languageCode != null && s.languageCode.equals(r.path("original_language").asText())) score += 2;
            double pop = r.path("popularity").asDouble(0);
            if (score > bestScore || (score == bestScore && score > 0 && pop > bestPopularity)) {
                bestScore = score;
                bestId = r.path("id").asInt();
                bestPopularity = pop;
            }
        }
        // Exact title plus year or language, or a near title with both. Generic titles ("Train", "Bail") need corroboration.
        return bestScore >= 5 ? bestId : null;
    }

    static String pickTrailer(JsonNode videos, String lang) {
        String bestKey = null;
        int bestScore = -1;
        String bestDate = "";
        for (JsonNode v : videos) {
            if (!"YouTube".equalsIgnoreCase(v.path("site").asText())) continue;
            String type = v.path("type").asText();
            int score = switch (type) {
                case "Trailer" -> 6;
                case "Teaser" -> 3;
                default -> -1;
            };
            if (score < 0) continue;
            if (v.path("official").asBoolean(false)) score += 3;
            String vl = v.path("iso_639_1").asText("");
            if (lang != null && lang.equals(vl)) score += 2;
            else if ("en".equals(vl)) score += 1;
            if (v.path("name").asText("").toLowerCase(Locale.ROOT).contains("official trailer")) score += 1;
            String date = v.path("published_at").asText("");
            if (score > bestScore || (score == bestScore && date.compareTo(bestDate) > 0)) {
                bestScore = score;
                bestKey = v.path("key").asText(null);
                bestDate = date;
            }
        }
        return bestKey;
    }

    // ------------------------------------------------------------------------------------------- apply

    private SyncResult apply(Long movieId, Integer tmdbId, JsonNode d, String trailerKey, String trailerSource) {
        Movie m = movieRepository.findById(movieId).orElseThrow();
        boolean firstMatch = m.getTmdbId() == null && tmdbId != null;
        boolean poster = false, trailer = false;
        List<String> notes = new ArrayList<>();
        if (tmdbId != null) {
            m.setTmdbId(tmdbId);
            notes.add("TMDB #" + tmdbId);
        } else {
            notes.add("No confident TMDB match");
        }
        if (d != null) {
            if (!m.isMediaLocked()) {
                String posterUrl = tmdb.image("w500", d.path("poster_path").asText(null));
                if (posterUrl != null && (m.getPosterUrl() == null || tmdb.isTmdbImage(m.getPosterUrl()) || "IMPORT".equals(m.getCatalogSource()))) {
                    poster = !posterUrl.equals(m.getPosterUrl());
                    m.setPosterUrl(posterUrl);
                }
                String backdrop = tmdb.image("w1280", d.path("backdrop_path").asText(null));
                if (backdrop != null && (m.getBackdropUrl() == null || tmdb.isTmdbImage(m.getBackdropUrl()) || "IMPORT".equals(m.getCatalogSource()))) {
                    m.setBackdropUrl(backdrop);
                }
            }
            String overview = d.path("overview").asText("").trim();
            if (!overview.isEmpty() && (m.getSynopsis() == null || PLACEHOLDER_SYNOPSES.contains(m.getSynopsis()))) {
                m.setSynopsis(overview);
            }
            String tagline = d.path("tagline").asText("").trim();
            if (!tagline.isEmpty() && m.getTagline() == null && tagline.length() <= 240) m.setTagline(tagline);
            int runtime = d.path("runtime").asInt(0);
            if (m.isRuntimeEstimated() && runtime >= 30 && runtime <= 400) {
                m.setDurationMin(runtime);
                m.setRuntimeEstimated(false);
            }
            String date = d.path("release_date").asText("");
            if (m.isReleaseDateEstimated() && date.length() == 10) {
                m.setReleaseDate(LocalDate.parse(date));
                m.setReleaseDateEstimated(false);
            }
            if (firstMatch && "IMPORT".equals(m.getCatalogSource())) {
                enrichPeopleAndGenres(m, d);
            }
            fillCastPhotos(m, d.path("credits").path("cast"));
            for (JsonNode c : d.path("credits").path("crew")) {
                if (!"Director".equals(c.path("job").asText())) continue;
                for (Director dir : m.getDirectors()) {
                    if (dir.getPhotoUrl() == null && normaliseTitle(dir.getName()).equals(normaliseTitle(c.path("name").asText()))) {
                        dir.setPhotoUrl(tmdb.image("w185", c.path("profile_path").asText(null)));
                    }
                }
            }
        }
        // Imported titles follow TMDB; titles created in the admin console only get a trailer when they have none.
        boolean trailerReplaceable = m.getTrailerUrl() == null || m.getTrailerUrl().contains(DEMO_TRAILER_ID)
                || "IMPORT".equals(m.getCatalogSource());
        if (trailerKey != null && !m.isMediaLocked() && trailerReplaceable) {
            String url = YouTubeUrls.embed(trailerKey);
            trailer = !url.equals(m.getTrailerUrl());
            m.setTrailerUrl(url);
            notes.add("trailer via " + ("TMDB".equals(trailerSource) ? "TMDB" : "YouTube search"));
        } else if (m.getTrailerUrl() != null && m.getTrailerUrl().contains(DEMO_TRAILER_ID) && !"DEMO".equals(m.getCatalogSource())) {
            m.setTrailerUrl(null);
        }
        if (trailerKey == null && m.getTrailerUrl() == null) notes.add("no trailer found");
        m.setMediaSyncedAt(Instant.now(clock));
        String note = String.join("; ", notes);
        m.setMediaSyncNote(note.length() > 200 ? note.substring(0, 200) : note);
        log.info("media_sync movieId={} title=\"{}\" tmdb={} poster={} trailer={}", m.getId(), m.getTitle(), tmdbId, poster, trailer);
        return new SyncResult(m.getId(), m.getTitle(), tmdbId != null, tmdbId, poster, trailer, trailerSource, note);
    }

    private void enrichPeopleAndGenres(Movie m, JsonNode d) {
        Map<String, Genre> bySlug = new HashMap<>();
        genreRepository.findAll().forEach(g -> bySlug.put(g.getSlug(), g));
        Set<Genre> genres = new LinkedHashSet<>();
        for (JsonNode g : d.path("genres")) {
            Genre genre = bySlug.get(GENRES.get(g.path("name").asText()));
            if (genre != null) genres.add(genre);
        }
        if (!genres.isEmpty()) {
            m.getGenres().clear();
            m.getGenres().addAll(genres);
        }
        if (m.getDirectors().isEmpty()) {
            for (JsonNode c : d.path("credits").path("crew")) {
                if (!"Director".equals(c.path("job").asText()) || m.getDirectors().size() >= 2) continue;
                String name = c.path("name").asText();
                Director dir = directorRepository.findFirstByNameIgnoreCase(name).orElseGet(() -> {
                    Director x = new Director();
                    x.setName(name);
                    return directorRepository.save(x);
                });
                if (dir.getPhotoUrl() == null) dir.setPhotoUrl(tmdb.image("w185", c.path("profile_path").asText(null)));
                m.getDirectors().add(dir);
            }
        }
        if (m.getCast().isEmpty()) {
            int order = 1;
            for (JsonNode c : d.path("credits").path("cast")) {
                if (order > 8) break;
                String name = c.path("name").asText();
                Actor actor = actorRepository.findFirstByNameIgnoreCase(name).orElseGet(() -> {
                    Actor a = new Actor();
                    a.setName(name);
                    return actorRepository.save(a);
                });
                MovieCast mc = new MovieCast();
                mc.setMovie(m);
                mc.setActor(actor);
                String character = c.path("character").asText("").trim();
                mc.setCharacterName(character.isEmpty() ? null : character.length() > 120 ? character.substring(0, 120) : character);
                mc.setBillingOrder(order++);
                m.getCast().add(mc);
            }
        }
    }

    /** Adds headshots (and character names) for cast members we already list. */
    private void fillCastPhotos(Movie m, JsonNode tmdbCast) {
        Map<String, JsonNode> byName = new HashMap<>();
        for (JsonNode c : tmdbCast) byName.putIfAbsent(normaliseTitle(c.path("name").asText()), c);
        for (MovieCast mc : m.getCast()) {
            JsonNode c = byName.get(normaliseTitle(mc.getActor().getName()));
            if (c == null) continue;
            if (mc.getActor().getPhotoUrl() == null) mc.getActor().setPhotoUrl(tmdb.image("w185", c.path("profile_path").asText(null)));
            String character = c.path("character").asText("").trim();
            if (mc.getCharacterName() == null && mc.getCrewRole() == null && !character.isEmpty() && character.length() <= 120) {
                mc.setCharacterName(character);
            }
        }
    }

    // ------------------------------------------------------------------------------------------- helpers

    private record Snapshot(String title, Integer year, String languageCode, Integer tmdbId, boolean mediaLocked, boolean hasRealTrailer) {}

    private Snapshot snapshot(Movie m) {
        Integer year = m.isReleaseDateEstimated() && m.getReleaseDate() != null && m.getReleaseDate().getYear() >= 2027
                ? null : (m.getReleaseDate() == null ? null : m.getReleaseDate().getYear());
        String lang = m.getLanguages().stream().findFirst().map(l -> LANGUAGE_CODES.get(l.toLowerCase(Locale.ROOT))).orElse(null);
        boolean realTrailer = m.getTrailerUrl() != null && !m.getTrailerUrl().contains(DEMO_TRAILER_ID);
        return new Snapshot(m.getTitle(), year, lang, m.getTmdbId(), m.isMediaLocked(), realTrailer);
    }

    static String normaliseTitle(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
                .replace("&", " and ").replaceAll("[^a-z0-9]+", " ").trim();
        if (n.startsWith("the ")) n = n.substring(4);
        return n.replaceAll("\\s+", " ");
    }

    private void evictMovieCache() {
        Cache c = cacheManager.getCache(CacheConfig.MOVIE_DETAIL);
        if (c != null) c.clear();
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
