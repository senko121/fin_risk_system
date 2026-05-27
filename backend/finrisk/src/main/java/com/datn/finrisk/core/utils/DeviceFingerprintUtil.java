package com.datn.finrisk.core.utils;

/**
 * Produces a stable device fingerprint from HTTP request headers.
 *
 * User-Agent alone is too volatile (changes on every browser version bump).
 * This util extracts only the OS family + browser family, which stays constant
 * across minor updates.  A frontend can also supply a more accurate fingerprint
 * via the X-Device-Fingerprint header; that value takes priority when present.
 *
 * Format when derived from UA: "OS|Browser"  e.g. "Windows|Chrome"
 */
public final class DeviceFingerprintUtil {

    private DeviceFingerprintUtil() {}

    public static final int MAX_LENGTH = 255;

    /**
     * Resolves a fingerprint, preferring the frontend-supplied header.
     *
     * @param userAgent          value of the User-Agent request header (may be null)
     * @param frontendFingerprint value of X-Device-Fingerprint header (may be null)
     */
    public static String resolve(String userAgent, String frontendFingerprint) {
        if (frontendFingerprint != null && !frontendFingerprint.isBlank()) {
            return frontendFingerprint.length() > MAX_LENGTH
                    ? frontendFingerprint.substring(0, MAX_LENGTH)
                    : frontendFingerprint;
        }
        return fromUserAgent(userAgent);
    }

    /**
     * Derives a stable fingerprint from a User-Agent string.
     * Strips version numbers so Chrome/148 and Chrome/149 map to the same value.
     */
    public static String fromUserAgent(String ua) {
        if (ua == null || ua.isBlank()) return "Unknown|Unknown";
        return detectOs(ua) + "|" + detectBrowser(ua);
    }

    private static String detectOs(String ua) {
        if (ua.contains("Android"))                        return "Android";
        if (ua.contains("iPhone") || ua.contains("iPad")) return "iOS";
        if (ua.contains("Windows NT"))                     return "Windows";
        if (ua.contains("Macintosh"))                      return "macOS";
        if (ua.contains("Linux"))                          return "Linux";
        return "Unknown";
    }

    private static String detectBrowser(String ua) {
        // Edge must be checked before Chrome (Edge UA contains "Chrome/")
        if (ua.contains("Edg/"))     return "Edge";
        if (ua.contains("Chrome/"))  return "Chrome";
        if (ua.contains("Firefox/")) return "Firefox";
        if (ua.contains("Safari/"))  return "Safari";
        if (ua.contains("PostmanRuntime")) return "Postman";
        return "Unknown";
    }
}
