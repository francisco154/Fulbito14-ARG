package com.fulbito14.arg;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Extracts M3U8 stream URLs from embed pages using OkHttp
 * v1.5 FIX: Now tracks the source domain to pass as Referer header
 * when ExoPlayer plays the M3U8 stream (fubohd.com requires Referer)
 */
public class M3U8Extractor {

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build();

    private static final Pattern PLAYBACK_URL_PATTERN = Pattern.compile(
            "playbackURL\\s*=\\s*\"([^\"]+)\""
    );

    private static final Pattern FUBOHD_PATTERN = Pattern.compile(
            "https?://[a-zA-Z0-9\\-_]+\\.fubohd\\.com:\\d+/[a-zA-Z0-9_]+/mono\\.m3u8\\?token=[a-zA-Z0-9\\-_]+"
    );

    private static final Pattern GENERIC_M3U8_PATTERN = Pattern.compile(
            "https?://[^\\s\"'<>]+\\.m3u8[^\\s\"'<>]*"
    );

    /**
     * Result object containing both the M3U8 URL and the referer domain
     */
    public static class ExtractResult {
        public final String m3u8Url;
        public final String referer;

        public ExtractResult(String m3u8Url, String referer) {
            this.m3u8Url = m3u8Url;
            this.referer = referer;
        }
    }

    /**
     * Extract M3U8 URL from an embed page, with referer tracking
     */
    public static ExtractResult extractM3U8WithReferer(String primaryUrl, String backupUrl) {
        // Try primary
        ExtractResult result = extractFromUrlWithReferer(primaryUrl);
        if (result != null) return result;

        // Try backup
        if (backupUrl != null && !backupUrl.isEmpty()) {
            result = extractFromUrlWithReferer(backupUrl);
            if (result != null) return result;
        }

        return null;
    }

    /**
     * Legacy method for backward compatibility
     */
    public static String extractM3U8(String primaryUrl, String backupUrl) {
        ExtractResult result = extractM3U8WithReferer(primaryUrl, backupUrl);
        return result != null ? result.m3u8Url : null;
    }

    /**
     * Fetch an embed page, extract M3U8 URL and track the referer
     */
    public static ExtractResult extractFromUrlWithReferer(String embedUrl) {
        try {
            Request request = new Request.Builder()
                    .url(embedUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 11; AndroidTV) AppleWebKit/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,*/*")
                    .header("Accept-Language", "es-AR,es;q=0.9")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return null;

                String html = response.body().string();
                String m3u8 = findM3U8InHtml(html);
                if (m3u8 == null) return null;

                // Determine the referer based on the URL we fetched from
                String referer = determineReferer(embedUrl, m3u8);
                return new ExtractResult(m3u8, referer);
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Legacy method
     */
    public static String extractFromUrl(String embedUrl) {
        ExtractResult result = extractFromUrlWithReferer(embedUrl);
        return result != null ? result.m3u8Url : null;
    }

    /**
     * Determine the correct Referer header based on source URL and M3U8 URL
     * fubohd.com requires a Referer from la12hd.com or la14hd.com
     */
    private static String determineReferer(String sourceUrl, String m3u8Url) {
        // If the M3U8 is from fubohd.com, we need a referer from the source site
        if (m3u8Url.contains("fubohd.com")) {
            if (sourceUrl.contains("la14hd.com")) {
                return "https://la14hd.com/";
            } else if (sourceUrl.contains("la12hd.com")) {
                return "https://la12hd.com/";
            } else if (sourceUrl.contains("ksdjugfsddeports.com")) {
                return "https://deportes.ksdjugfsddeports.com/";
            }
            // Default to la14hd.com as most streams work with it
            return "https://la14hd.com/";
        }
        // For other M3U8 sources, use the source domain as referer
        return sourceUrl;
    }

    /**
     * Search for M3U8 URLs in HTML content
     * Priority: playbackURL variable > fubohd.com pattern > generic .m3u8
     */
    public static String findM3U8InHtml(String html) {
        // Pattern 0: Look for playbackURL variable (most reliable)
        Matcher playbackMatch = PLAYBACK_URL_PATTERN.matcher(html);
        if (playbackMatch.find()) {
            String url = playbackMatch.group(1);
            if (url.contains(".m3u8")) {
                return url;
            }
        }

        // Pattern 1: fubohd.com (from la12hd/la14hd)
        Matcher fuboMatch = FUBOHD_PATTERN.matcher(html);
        if (fuboMatch.find()) return fuboMatch.group(0);

        // Pattern 2: Generic M3U8
        Matcher genericMatch = GENERIC_M3U8_PATTERN.matcher(html);
        if (genericMatch.find()) {
            String url = genericMatch.group(0);
            url = url.replaceAll("[,;\\])}>]+$", "");
            return url;
        }

        return null;
    }
}
