package com.sebastianhauss.wayfare.util;

/**
 * Deliberately tiny, dependency-free User-Agent heuristics. Good enough for a
 * clicks-by-device / clicks-by-browser breakdown; not meant to be exhaustive.
 * Check order matters: Edge and Chrome both advertise "chrome", Chrome also
 * advertises "safari", so the more specific tokens are tested first.
 */
public final class UserAgentParser {

    private UserAgentParser() {
    }

    public static String deviceType(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        String ua = userAgent.toLowerCase();
        if (ua.contains("bot") || ua.contains("crawler") || ua.contains("spider")) {
            return "bot";
        }
        if (ua.contains("ipad") || ua.contains("tablet")) {
            return "tablet";
        }
        if (ua.contains("mobi") || ua.contains("iphone") || ua.contains("android")) {
            return "mobile";
        }
        return "desktop";
    }

    public static String browser(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        String ua = userAgent.toLowerCase();
        if (ua.contains("edg")) {
            return "Edge";
        }
        if (ua.contains("opr") || ua.contains("opera")) {
            return "Opera";
        }
        if (ua.contains("firefox") || ua.contains("fxios")) {
            return "Firefox";
        }
        if (ua.contains("chrome") || ua.contains("crios")) {
            return "Chrome";
        }
        if (ua.contains("safari")) {
            return "Safari";
        }
        return "Other";
    }
}
