package com.sebastianhauss.wayfare.dto;

import com.sebastianhauss.wayfare.util.UserAgentParser;

import java.net.URI;

/**
 * The bits of a redirect request we keep for analytics. Everything is derived
 * (domain only, 2-letter country, coarse device/browser) so we never persist raw
 * IPs or full User-Agent strings.
 */
public record ClickMetadata(String referrerDomain, String country, String deviceType, String browser) {

    public static ClickMetadata empty() {
        return new ClickMetadata(null, null, null, null);
    }

    public static ClickMetadata from(String referer, String userAgent, String country) {
        return new ClickMetadata(
                referrerDomain(referer),
                normalizeCountry(country),
                UserAgentParser.deviceType(userAgent),
                UserAgentParser.browser(userAgent));
    }

    private static String referrerDomain(String referer) {
        if (referer == null || referer.isBlank()) {
            return null;
        }
        try {
            String host = URI.create(referer).getHost();
            if (host == null || host.isBlank()) {
                return null;
            }
            host = host.toLowerCase();
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String normalizeCountry(String country) {
        if (country == null || country.length() != 2) {
            return null;
        }
        return country.toUpperCase();
    }
}
