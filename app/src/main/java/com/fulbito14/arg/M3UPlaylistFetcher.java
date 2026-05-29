package com.fulbito14.arg;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Fetches and parses M3U playlists from community-maintained sources
 * v2.2: Replaces embed-based system with direct M3U playlist parsing
 *
 * Sources:
 * 1. iptv-org Argentina (auto-updated by community)
 * 2. iptv-org Sports category
 * 3. iptv-org Spanish language
 * 4. radiosargentina.com.ar
 * 5. m3u.cl
 */
public class M3UPlaylistFetcher {

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build();

    // Playlist source URLs
    private static final String[] PLAYLIST_URLS = {
        "https://iptv-org.github.io/iptv/countries/ar.m3u",           // Argentina
        "https://iptv-org.github.io/iptv/categories/sports.m3u",      // Sports worldwide
        "https://iptv-org.github.io/iptv/languages/spa.m3u",          // Spanish language
        "https://radiosargentina.com.ar/TVAR.m3u",                    // Argentina curated
    };

    // M3U metadata parsing patterns
    private static final Pattern TVG_NAME = Pattern.compile("tvg-name=\"([^\"]*)\"");
    private static final Pattern TVG_LOGO = Pattern.compile("tvg-logo=\"([^\"]*)\"");
    private static final Pattern GROUP_TITLE = Pattern.compile("group-title=\"([^\"]*)\"");
    private static final Pattern TVG_ID = Pattern.compile("tvg-id=\"([^\"]*)\"");
    // Fallback: channel name after last comma in EXTINF line
    private static final Pattern COMMA_NAME = Pattern.compile(",([^,]+)$");

    // Keyword filters to find relevant channels
    private static final String[] SPORT_KEYWORDS = {
        "espn", "fox sports", "tnt sports", "tyc", "dsports", "directv sport",
        "deport", "sport", "futbol", "football", "win sport", "tudn",
        "golf", "tennis", "nba", "nfl", "mlb", "ufc", "boxing", "boxeo",
        "tve", "telefe", "trece", "america tv", "canal 9", "tv publica",
        "deportv", "gol", "latino", "argentina"
    };

    private static final String[] BLOCK_KEYWORDS = {
        "test", "prueba", "offline", "nsfw", "xxx", "porn", "adult"
    };

    /**
     * Fetch all channels from all playlist sources
     * Merges duplicates, prioritizes by category relevance
     */
    public static List<Channel> fetchAllChannels() {
        List<Channel> allChannels = new ArrayList<>();
        Set<String> seenNames = new LinkedHashSet<>();

        for (String url : PLAYLIST_URLS) {
            try {
                String source = getSourceName(url);
                List<Channel> channels = fetchAndParse(url, source);
                for (Channel ch : channels) {
                    String key = ch.name.toLowerCase().replaceAll("[^a-z0-9]", "");
                    if (!seenNames.contains(key)) {
                        seenNames.add(key);
                        allChannels.add(ch);
                    }
                }
            } catch (Exception e) {
                // Skip failed sources, continue with others
            }
        }

        // Sort: Sports first, then other categories
        allChannels.sort((a, b) -> {
            boolean aSport = a.isSport();
            boolean bSport = b.isSport();
            if (aSport && !bSport) return -1;
            if (!aSport && bSport) return 1;
            return a.name.compareToIgnoreCase(b.name);
        });

        // Re-number
        for (int i = 0; i < allChannels.size(); i++) {
            allChannels.get(i).number = i + 1;
            allChannels.get(i).id = i + 1;
        }

        return allChannels;
    }

    /**
     * Fetch and parse a single M3U playlist
     */
    public static List<Channel> fetchAndParse(String playlistUrl, String source) {
        List<Channel> channels = new ArrayList<>();

        try {
            Request request = new Request.Builder()
                    .url(playlistUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 11; AndroidTV) AppleWebKit/537.36")
                    .header("Accept", "*/*")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return channels;

                String m3uContent = response.body().string();
                channels = parseM3U(m3uContent, source);
            }
        } catch (Exception e) {
            // Network error, return empty
        }

        return channels;
    }

    /**
     * Parse M3U playlist content into Channel objects
     */
    public static List<Channel> parseM3U(String m3uContent, String source) {
        List<Channel> channels = new ArrayList<>();

        if (m3uContent == null || m3uContent.isEmpty()) return channels;
        if (!m3uContent.contains("#EXTINF")) return channels;

        String[] lines = m3uContent.split("\n");
        String currentExtinf = null;

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            if (line.startsWith("#EXTINF")) {
                currentExtinf = line;
            } else if (currentExtinf != null && !line.startsWith("#")) {
                // This is a stream URL line
                if (isValidStreamUrl(line)) {
                    Channel ch = parseChannel(currentExtinf, line, source);
                    if (ch != null && !isBlocked(ch.name)) {
                        channels.add(ch);
                    }
                }
                currentExtinf = null;
            }
        }

        return channels;
    }

    /**
     * Parse a single EXTINF line + URL into a Channel
     */
    private static Channel parseChannel(String extinf, String streamUrl, String source) {
        try {
            String name = extractValue(extinf, TVG_NAME);
            if (name == null || name.isEmpty()) {
                name = extractValue(extinf, COMMA_NAME);
            }
            if (name == null || name.isEmpty()) {
                name = "Unknown Channel";
            }
            name = name.trim();

            String logoUrl = extractValue(extinf, TVG_LOGO);
            String category = extractValue(extinf, GROUP_TITLE);
            String tvgId = extractValue(extinf, TVG_ID);

            if (category == null || category.isEmpty()) {
                category = guessCategory(name, streamUrl);
            }

            String country = guessCountry(tvgId, name);
            String logoKey = guessLogoKey(name);
            String description = name + " - " + category;

            return new Channel(0, name, 0, category, country, logoKey,
                             streamUrl, logoUrl, description, source);
        } catch (Exception e) {
            return null;
        }
    }

    private static String extractValue(String text, Pattern pattern) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private static boolean isValidStreamUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        String lower = url.toLowerCase();
        return lower.startsWith("http") &&
               (lower.contains(".m3u8") || lower.contains(".m3u") ||
                lower.contains(".mpd") || lower.contains("/live/") ||
                lower.contains("/stream/") || lower.contains("/playlist") ||
                lower.contains("index.m3u8") || lower.contains("playlist.m3u8"));
    }

    private static boolean isBlocked(String name) {
        if (name == null) return true;
        String lower = name.toLowerCase();
        for (String kw : BLOCK_KEYWORDS) {
            if (lower.contains(kw)) return true;
        }
        return false;
    }

    private static String guessCategory(String name, String url) {
        String lower = name.toLowerCase();
        if (lower.contains("sport") || lower.contains("deport") || lower.contains("espn") ||
            lower.contains("fox sport") || lower.contains("tnt sport") || lower.contains("tyc") ||
            lower.contains("futbol") || lower.contains("gol") || lower.contains("win sport") ||
            lower.contains("nba") || lower.contains("nfl") || lower.contains("ufc") ||
            lower.contains("tudn") || lower.contains("dsports") || lower.contains("directv sport")) {
            return "Deportes";
        }
        if (lower.contains("news") || lower.contains("noticia") || lower.contains("tn") ||
            lower.contains("cronica") || lower.contains("c5n") || lower.contains("a24")) {
            return "Noticias";
        }
        if (lower.contains("movie") || lower.contains("pelicula") || lower.contains("cinema") ||
            lower.contains("cine")) {
            return "Peliculas";
        }
        if (lower.contains("kids") || lower.contains("infantil") || lower.contains("cartoon") ||
            lower.contains("disney") || lower.contains("nick")) {
            return "Infantil";
        }
        if (lower.contains("music") || lower.contains("musica")) {
            return "Musica";
        }
        return "Entretenimiento";
    }

    private static String guessCountry(String tvgId, String name) {
        if (tvgId != null) {
            if (tvgId.endsWith(".ar")) return "Argentina";
            if (tvgId.endsWith(".br")) return "Brasil";
            if (tvgId.endsWith(".mx")) return "Mexico";
            if (tvgId.endsWith(".co")) return "Colombia";
            if (tvgId.endsWith(".cl")) return "Chile";
            if (tvgId.endsWith(".uy")) return "Uruguay";
            if (tvgId.endsWith(".pe")) return "Peru";
        }
        String lower = name.toLowerCase();
        if (lower.contains("argentina") || lower.contains("buenos aires")) return "Argentina";
        if (lower.contains("brasil") || lower.contains("globo") || lower.contains("sportv")) return "Brasil";
        if (lower.contains("mexic") || lower.contains("tudn") || lower.contains("azteca")) return "Mexico";
        if (lower.contains("colombia") || lower.contains("win sport")) return "Colombia";
        return "Latinoamerica";
    }

    private static String guessLogoKey(String name) {
        if (name == null) return "tv";
        String lower = name.toLowerCase();
        if (lower.contains("espn")) return "espn";
        if (lower.contains("fox sport")) return "fox";
        if (lower.contains("tnt sport")) return "tnt";
        if (lower.contains("tyc")) return "tyc";
        if (lower.contains("dsport") || lower.contains("directv sport")) return "dsports";
        if (lower.contains("win sport")) return "win";
        if (lower.contains("tudn")) return "tudn";
        if (lower.contains("telefe")) return "telefe";
        if (lower.contains("trece") || lower.contains("eltrece") || lower.contains("el trece")) return "trece";
        if (lower.contains("america")) return "america";
        if (lower.contains("canal 9") || lower.contains("nueve")) return "canal9";
        if (lower.contains("publica") || lower.contains("tv publica")) return "publica";
        if (lower.contains("deportv")) return "deportv";
        if (lower.contains("tn") || lower.contains("noticia")) return "tn";
        if (lower.contains("globo")) return "globo";
        if (lower.contains("sportv")) return "sportv";
        if (lower.contains("band")) return "band";
        return "tv";
    }

    private static String getSourceName(String url) {
        if (url.contains("iptv-org")) return "iptv-org";
        if (url.contains("radiosargentina")) return "radiosargentina";
        if (url.contains("m3u.cl")) return "m3u.cl";
        return "unknown";
    }
}
