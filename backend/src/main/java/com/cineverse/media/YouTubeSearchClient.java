package com.cineverse.media;

import com.cineverse.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Locale;

/**
 * Optional fallback: finds an embeddable official trailer on YouTube when TMDB has none.
 * Uses the YouTube Data API v3 search endpoint (100 quota units per call; the free daily quota is 10,000).
 */
@Slf4j
@Component
public class YouTubeSearchClient {
    private final AppProperties.Media config;
    private final ObjectMapper objectMapper;
    private final RestClient http;

    public YouTubeSearchClient(AppProperties props, ObjectMapper objectMapper, RestClient.Builder builder) {
        this.config = props.media();
        this.objectMapper = objectMapper;
        this.http = builder.clone().baseUrl("https://www.googleapis.com").build();
    }

    public boolean enabled() {
        return config != null && config.youtubeConfigured();
    }

    /** Returns a video id whose title looks like this film's trailer, or null. */
    public String findTrailer(String title, Integer year, String languageCode) {
        String q = title + (year != null ? " " + year : "") + " official trailer";
        try {
            String body = http.get().uri(b -> {
                var u = b.path("/youtube/v3/search").queryParam("part", "snippet").queryParam("type", "video")
                        .queryParam("videoEmbeddable", "true").queryParam("maxResults", "6").queryParam("regionCode", "IN")
                        .queryParam("q", q).queryParam("key", config.youtubeApiKey().trim());
                if (languageCode != null) u.queryParam("relevanceLanguage", languageCode);
                return u.build();
            }).retrieve().body(String.class);
            JsonNode items = objectMapper.readTree(body).path("items");
            String want = MediaSyncService.normaliseTitle(title);
            String firstWord = want.isEmpty() ? "" : want.split(" ")[0];
            for (JsonNode item : items) {
                String videoTitle = item.path("snippet").path("title").asText("").toLowerCase(Locale.ROOT);
                String norm = MediaSyncService.normaliseTitle(videoTitle);
                boolean trailer = videoTitle.contains("trailer") || videoTitle.contains("teaser");
                if (trailer && (norm.contains(want) || (!firstWord.isEmpty() && norm.contains(firstWord)))) {
                    return item.path("id").path("videoId").asText(null);
                }
            }
            return null;
        } catch (Exception e) {
            log.info("youtube_search_failed title={} reason={}", title, e.getMessage());
            return null;
        }
    }
}
