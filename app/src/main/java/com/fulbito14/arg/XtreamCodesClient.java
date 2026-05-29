package com.fulbito14.arg;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Xtream Codes API client - learned from Infiniti Stream V4 decompilation
 * v2.4: Connect to any Xtream Codes IPTV server for channel lists
 *
 * Standard XC API endpoints:
 * - /player_api.php?username=X&password=Y (login check)
 * - /player_api.php?username=X&password=Y&action=get_live_categories
 * - /player_api.php?username=X&password=Y&action=get_live_streams
 * - /player_api.php?username=X&password=Y&action=get_vod_categories
 * - /player_api.php?username=X&password=Y&action=get_vod_streams
 */
public class XtreamCodesClient {

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build();

    /**
     * Test connection to XC server
     */
    public static boolean testConnection(String serverUrl, String username, String password) {
        try {
            String url = buildApiUrl(serverUrl, username, password, null);
            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String body = response.body().string();
                    JSONObject json = new JSONObject(body);
                    JSONObject userInfo = json.optJSONObject("user_info");
                    if (userInfo != null) {
                        String status = userInfo.optString("status", "");
                        return "Active".equalsIgnoreCase(status) || "active".equalsIgnoreCase(status);
                    }
                }
            }
        } catch (Exception e) {
            // Connection failed
        }
        return false;
    }

    /**
     * Fetch live categories from XC server
     * Returns list of String[]{categoryId, categoryName}
     */
    public static List<String[]> fetchCategories(String serverUrl, String username, String password) {
        List<String[]> categories = new ArrayList<>();
        try {
            String url = buildApiUrl(serverUrl, username, password, "get_live_categories");
            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    JSONArray arr = new JSONArray(response.body().string());
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject cat = arr.getJSONObject(i);
                        String id = cat.optString("category_id", "");
                        String name = cat.optString("category_name", "");
                        categories.add(new String[]{id, name});
                    }
                }
            }
        } catch (Exception e) {
            // Failed to fetch categories
        }
        return categories;
    }

    /**
     * Fetch live streams from XC server and convert to Channel objects
     */
    public static List<Channel> fetchLiveStreams(String serverUrl, String username, String password) {
        List<Channel> channels = new ArrayList<>();
        try {
            // First fetch categories for better categorization
            List<String[]> categories = fetchCategories(serverUrl, username, password);
            java.util.HashMap<String, String> categoryMap = new java.util.HashMap<>();
            for (String[] cat : categories) {
                categoryMap.put(cat[0], cat[1]);
            }

            String url = buildApiUrl(serverUrl, username, password, "get_live_streams");
            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    JSONArray arr = new JSONArray(response.body().string());
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject stream = arr.getJSONObject(i);
                        String name = stream.optString("name", "Unknown");
                        int streamId = stream.optInt("stream_id", 0);
                        String logo = stream.optString("stream_icon", "");
                        String categoryId = stream.optString("category_id", "");
                        String epgId = stream.optString("epg_channel_id", "");

                        // Build stream URL: server + /live/ + username + / + password + / + streamId + .m3u8
                        String baseUrl = serverUrl.replaceAll("/+$", "");
                        String streamUrl = baseUrl + "/live/" + username + "/" + password + "/" + streamId + ".m3u8";

                        // Use category name from server if available, otherwise guess
                        String category;
                        if (categoryMap.containsKey(categoryId) && !categoryMap.get(categoryId).isEmpty()) {
                            category = categoryMap.get(categoryId);
                        } else {
                            category = guessCategory(categoryId, name);
                        }

                        String country = guessCountry(name);
                        String logoKey = M3UPlaylistFetcher.guessLogoKeyStatic(name);

                        Channel ch = new Channel(i + 1, name, i + 1, category, country,
                                logoKey, streamUrl, logo, name, "xc-server");
                        channels.add(ch);
                    }
                }
            }
        } catch (Exception e) {
            // Failed to fetch streams
        }
        return channels;
    }

    /**
     * Build XC API URL
     */
    private static String buildApiUrl(String serverUrl, String username, String password, String action) {
        StringBuilder sb = new StringBuilder();
        if (!serverUrl.startsWith("http")) sb.append("http://");
        sb.append(serverUrl.replaceAll("/+$", ""));
        sb.append("/player_api.php?username=").append(username);
        sb.append("&password=").append(password);
        if (action != null) sb.append("&action=").append(action);
        return sb.toString();
    }

    /**
     * Guess channel category from name (fallback when server doesn't provide categories)
     */
    private static String guessCategory(String categoryId, String name) {
        String lower = name.toLowerCase();
        if (lower.contains("sport") || lower.contains("deport") || lower.contains("espn") ||
            lower.contains("fox sport") || lower.contains("tnt sport") || lower.contains("tyc") ||
            lower.contains("futbol") || lower.contains("gol") || lower.contains("win sport")) {
            return "Deportes";
        }
        if (lower.contains("noticia") || lower.contains("news") || lower.contains("tn")) {
            return "Noticias";
        }
        if (lower.contains("pelicula") || lower.contains("movie") || lower.contains("cine")) {
            return "Peliculas";
        }
        if (lower.contains("infantil") || lower.contains("kids") || lower.contains("cartoon")) {
            return "Infantil";
        }
        if (lower.contains("musica") || lower.contains("music")) {
            return "Musica";
        }
        return "Entretenimiento";
    }

    /**
     * Guess channel country from name
     */
    private static String guessCountry(String name) {
        String lower = name.toLowerCase();
        if (lower.contains("argentina") || lower.contains("buenos aires")) return "Argentina";
        if (lower.contains("brasil") || lower.contains("globo") || lower.contains("sportv")) return "Brasil";
        if (lower.contains("mexic") || lower.contains("azteca")) return "Mexico";
        if (lower.contains("colombia") || lower.contains("win sport")) return "Colombia";
        if (lower.contains("chile")) return "Chile";
        return "Latinoamerica";
    }
}
