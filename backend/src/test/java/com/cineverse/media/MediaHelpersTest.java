package com.cineverse.media;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure unit tests for trailer/link helpers (no Spring, no network). */
class MediaHelpersTest {

    @Test
    void normalisesEveryYouTubeLinkShapeToAnEmbedUrl() {
        String embed = "https://www.youtube.com/embed/dQw4w9WgXcQ";
        assertThat(YouTubeUrls.normalise("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=10s")).isEqualTo(embed);
        assertThat(YouTubeUrls.normalise("https://youtu.be/dQw4w9WgXcQ?si=abc")).isEqualTo(embed);
        assertThat(YouTubeUrls.normalise("https://www.youtube.com/shorts/dQw4w9WgXcQ")).isEqualTo(embed);
        assertThat(YouTubeUrls.normalise("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ")).isEqualTo(embed);
        assertThat(YouTubeUrls.normalise("dQw4w9WgXcQ")).isEqualTo(embed);
        assertThat(YouTubeUrls.normalise("  ")).isNull();
        assertThat(YouTubeUrls.normalise("https://vimeo.com/123")).isEqualTo("https://vimeo.com/123");
    }

    @Test
    void prefersOfficialTrailerInTheFilmsLanguage() throws Exception {
        var videos = new ObjectMapper().readTree("""
                [
                  {"site":"YouTube","type":"Teaser","official":true,"iso_639_1":"te","key":"teaserTE001","published_at":"2026-01-01"},
                  {"site":"YouTube","type":"Trailer","official":false,"iso_639_1":"en","key":"fanTrailer1","published_at":"2026-02-01"},
                  {"site":"YouTube","type":"Trailer","official":true,"iso_639_1":"te","key":"officialTE1","published_at":"2026-03-01"},
                  {"site":"Vimeo","type":"Trailer","official":true,"iso_639_1":"te","key":"vimeo000001","published_at":"2026-04-01"}
                ]""");
        assertThat(MediaSyncService.pickTrailer(videos, "te")).isEqualTo("officialTE1");
        assertThat(MediaSyncService.pickTrailer(new ObjectMapper().readTree("[]"), "te")).isNull();
    }

    @Test
    void titleNormalisationIgnoresCaseAccentsPunctuationAndLeadingThe() {
        assertThat(MediaSyncService.normaliseTitle("The Odyssey")).isEqualTo("odyssey");
        assertThat(MediaSyncService.normaliseTitle("Amélie")).isEqualTo("amelie");
        assertThat(MediaSyncService.normaliseTitle("Dhurandhar: The Revenge")).isEqualTo("dhurandhar the revenge");
    }
}
