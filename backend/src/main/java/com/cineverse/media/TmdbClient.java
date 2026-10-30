package com.cineverse.media;

import com.cineverse.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.util.function.Function;

/**
 * Minimal client for The Movie Database (TMDB) v3 API. Accepts either a v3 API key (sent as {@code api_key})
 * or a v4 "API Read Access Token" (sent as a bearer token). Keys never leave the backend.
 */
@Slf4j
@Component
public class TmdbClient {
    private final AppProperties.Media config;
    private final ObjectMapper objectMapper;
    private final RestClient http;

    public TmdbClient(AppProperties props, ObjectMapper objectMapper, RestClient.Builder builder) {
        this.config = props.media();
        this.objectMapper = objectMapper;
        String base = config == null || config.tmdbApiBase() == null ? "https://api.themoviedb.org" : config.tmdbApiBase();
        this.http = builder.clone().baseUrl(base).build();
    }

    public boolean enabled() {
        return config != null && config.tmdbConfigured();
    }

    /** GET /3/search/movie. {@code year} may be null. */
    public JsonNode searchMovie(String title, Integer year) {
        return get(b -> {
            UriBuilder u = b.path("/3/search/movie").queryParam("query", title).queryParam("include_adult", "false")
                    .queryParam("language", "en-US").queryParam("page", "1");
            if (year != null) u.queryParam("year", year);
            return withKey(u).build();
        });
    }

    /** GET /3/movie/{id} with videos (trailers in Indian languages + English) and credits appended. */
    public JsonNode movieDetails(int tmdbId) {
        return get(b -> withKey(b.path("/3/movie/{id}").queryParam("language", "en-US")
                .queryParam("append_to_response", "videos,credits")
                .queryParam("include_video_language", "en,hi,te,ta,ml,kn,ja,ko,fr,es,de,it,null")).build(tmdbId));
    }

    public String image(String size, String path) {
        if (path == null || path.isBlank() || "null".equals(path)) return null;
        String base = config.tmdbImageBase() == null ? "https://image.tmdb.org/t/p" : config.tmdbImageBase();
        return base + "/" + size + path;
    }

    public boolean isTmdbImage(String url) {
        return url != null && (url.startsWith(config.tmdbImageBase() == null ? "https://image.tmdb.org" : config.tmdbImageBase())
                || url.startsWith("https://image.tmdb.org"));
    }

    private boolean bearer() {
        return config.tmdbApiKey().trim().startsWith("eyJ");
    }

    private UriBuilder withKey(UriBuilder u) {
        return bearer() ? u : u.queryParam("api_key", config.tmdbApiKey().trim());
    }

    private JsonNode get(Function<UriBuilder, URI> uri) {
        try {
            RestClient.RequestHeadersSpec<?> req = http.get().uri(uri);
            if (bearer()) req = req.header(HttpHeaders.AUTHORIZATION, "Bearer " + config.tmdbApiKey().trim());
            String body = req.header(HttpHeaders.ACCEPT, "application/json").retrieve().body(String.class);
            return objectMapper.readTree(body);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 401) throw new MediaSyncException("TMDB rejected the API key (401). Check TMDB_API_KEY.");
            if (e.getStatusCode().value() == 404) return objectMapper.createObjectNode();
            throw new MediaSyncException("TMDB error " + e.getStatusCode().value());
        } catch (MediaSyncException e) {
            throw e;
        } catch (Exception e) {
            throw new MediaSyncException("Could not reach TMDB: " + e.getClass().getSimpleName()
                    + " (if api.themoviedb.org is blocked on your network, switch DNS to 1.1.1.1 or 8.8.8.8)");
        }
    }

    public static class MediaSyncException extends RuntimeException {
        public MediaSyncException(String message) { super(message); }
    }
}
