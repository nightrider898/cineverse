package com.cineverse.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

/** Strongly typed view over the {@code cineverse.*} configuration tree. */
@ConfigurationProperties(prefix = "cineverse")
public record AppProperties(
        @DefaultValue("CineVerse") String appName,
        @DefaultValue("http://localhost:3000") String frontendUrl,
        @DefaultValue("Asia/Kolkata") String timezone,
        Cors cors,
        Jwt jwt,
        Cookies cookies,
        SeatLock seatLock,
        EventHold eventHold,
        PayAtVenue payAtVenue,
        Pricing pricing,
        Cancellation cancellation,
        RateLimit rateLimit,
        Payments payments,
        Email email,
        Qr qr,
        Seed seed,
        Media media,
        Brand brand
) {
    public record Cors(List<String> allowedOrigins) {}

    public record Jwt(String secret, Duration accessExpiration, Duration refreshExpiration, @DefaultValue("cineverse") String issuer) {}

    public record Cookies(@DefaultValue("true") boolean secure, @DefaultValue("Lax") String sameSite) {}

    public record SeatLock(@DefaultValue("PT5M") Duration duration, @DefaultValue("10") int maxSeats, @DefaultValue("5000") long cleanupIntervalMs) {}

    public record EventHold(@DefaultValue("PT10M") Duration duration) {}

    public record PayAtVenue(@DefaultValue("PT2H") Duration holdDuration, @DefaultValue("PT30M") Duration cutoffBeforeShow) {}

    public record Pricing(BigDecimal convenienceFeePerTicket, BigDecimal convenienceFeeMax, BigDecimal taxRateOnFee, @DefaultValue("INR") String currency) {}

    public record Cancellation(@DefaultValue("120") int cutoffMinutesBeforeShow, @DefaultValue("100") int refundPercent, @DefaultValue("false") boolean refundConvenienceFee) {}

    public record RateLimit(@DefaultValue("true") boolean enabled, @DefaultValue("20") int authPerMinute, @DefaultValue("300") int generalPerMinute) {}

    public record Payments(boolean mockEnabled, Stripe stripe, Razorpay razorpay, PhonePe phonepe) {
        public record Stripe(String secretKey, String publishableKey, String webhookSecret, String apiBase) {
            public boolean configured() { return notBlank(secretKey) && notBlank(publishableKey); }
        }
        public record Razorpay(String keyId, String keySecret, String webhookSecret, String apiBase) {
            public boolean configured() { return notBlank(keyId) && notBlank(keySecret); }
        }
        public record PhonePe(String clientId, String clientSecret, String clientVersion, String environment,
                              String webhookUsername, String webhookPassword) {
            public boolean configured() { return notBlank(clientId) && notBlank(clientSecret); }
        }
    }

    /**
     * @param provider    console (writes rendered HTML to {@code outboxDir}) or smtp
     * @param loginAlerts email a "new sign-in" notice on every successful password login
     */
    public record Email(@DefaultValue("console") String provider, String from, @DefaultValue("true") boolean loginAlerts,
                        @DefaultValue("email-outbox") String outboxDir) {}

    /**
     * Posters, backdrops, trailers and missing details come from The Movie Database (TMDB) API; YouTube Data API
     * search is an optional fallback for trailers TMDB does not list.
     *
     * @param tmdbApiKey    TMDB v3 API key or v4 "API Read Access Token" (starts with "eyJ")
     * @param youtubeApiKey optional YouTube Data API v3 key
     */
    public record Media(String tmdbApiKey, String youtubeApiKey, @DefaultValue("true") boolean syncOnStartup,
                        @DefaultValue("P7D") Duration resyncAfter, @DefaultValue("https://api.themoviedb.org") String tmdbApiBase,
                        @DefaultValue("https://image.tmdb.org/t/p") String tmdbImageBase) {
        public boolean tmdbConfigured() { return notBlank(tmdbApiKey); }
        public boolean youtubeConfigured() { return notBlank(youtubeApiKey); }
    }

    /** Company identity used in emails (header, footer, support block). */
    public record Brand(@DefaultValue("BookMyEvent") String name, String legalName, String motto, String supportEmail,
                        String supportPhone, String address, String instagramUrl, String xUrl, String facebookUrl,
                        String youtubeUrl, String linkedinUrl) {}

    public record Qr(String secret) {}

    public record Seed(boolean demoData, String demoPassword, @DefaultValue("6") int showDaysAhead) {}

    static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
