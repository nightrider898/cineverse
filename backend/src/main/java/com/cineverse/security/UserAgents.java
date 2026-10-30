package com.cineverse.security;

/** Turns a User-Agent header into "Chrome on Windows" style text for security emails. */
public final class UserAgents {
    private UserAgents() {}

    public static String describe(String ua) {
        if (ua == null || ua.isBlank()) return "Unknown device";
        String browser = ua.contains("Edg/") ? "Edge" : ua.contains("OPR/") || ua.contains("Opera") ? "Opera"
                : ua.contains("SamsungBrowser/") ? "Samsung Internet" : ua.contains("Firefox/") ? "Firefox"
                : ua.contains("Chrome/") || ua.contains("CriOS/") ? "Chrome" : ua.contains("Safari/") ? "Safari" : null;
        String os = ua.contains("Windows") ? "Windows" : ua.contains("Android") ? "Android"
                : ua.contains("iPhone") || ua.contains("iPad") ? "iOS" : ua.contains("Mac OS X") || ua.contains("Macintosh") ? "macOS"
                : ua.contains("CrOS") ? "ChromeOS" : ua.contains("Linux") ? "Linux" : null;
        if (browser == null && os == null) {
            String first = ua.split("[ /]")[0];
            return first.length() > 40 ? first.substring(0, 40) : first;
        }
        return browser == null ? os : os == null ? browser : browser + " on " + os;
    }

}
