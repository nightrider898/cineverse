package com.cineverse.media;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Normalises the many shapes of YouTube links into the embeddable form the movie page plays. */
public final class YouTubeUrls {
    private YouTubeUrls() {}

    private static final Pattern ID = Pattern.compile("^[A-Za-z0-9_-]{11}$");
    private static final Pattern[] PATTERNS = {
            Pattern.compile("(?:youtube\\.com|youtube-nocookie\\.com)/(?:embed|shorts|live|v)/([A-Za-z0-9_-]{11})"),
            Pattern.compile("youtube\\.com/watch\\?(?:.*&)?v=([A-Za-z0-9_-]{11})"),
            Pattern.compile("youtu\\.be/([A-Za-z0-9_-]{11})"),
    };

    /** Returns the 11-character video id, or null when the text is not a YouTube link/id. */
    public static String videoId(String urlOrId) {
        if (urlOrId == null) return null;
        String s = urlOrId.trim();
        if (ID.matcher(s).matches()) return s;
        for (Pattern p : PATTERNS) {
            Matcher m = p.matcher(s);
            if (m.find()) return m.group(1);
        }
        return null;
    }

    public static String embed(String videoId) {
        return "https://www.youtube.com/embed/" + videoId;
    }

    /** YouTube links become embed URLs; anything else is returned trimmed (or null when blank). */
    public static String normalise(String urlOrId) {
        if (urlOrId == null || urlOrId.isBlank()) return null;
        String id = videoId(urlOrId);
        return id == null ? urlOrId.trim() : embed(id);
    }
}
