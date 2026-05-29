package com.fulbito14.arg;

import android.util.Log;

import com.rtx.smar4.Config.mConfig;
import com.rtx.smar4.UI.SplashRTX;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Fetches channels from Infiniti Stream API
 * Uses the native librtx_rebrand.so library to bypass Cloudflare protection
 * and obtain XC server credentials, then uses standard XC API to get channels
 *
 * v2.5: Replaces both M3UPlaylistFetcher and XtreamCodesClient
 */
public class InfinitiStreamSource {

    private static final String TAG = "InfinitiStream";

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build();

    /**
     * Fetch channels from Infiniti Stream API
     * Flow:
     * 1. Use native performHttpsGet to fetch config from API (bypasses Cloudflare)
     * 2. Parse the response to extract XC server URL + credentials
     * 3. Use standard XC API to get live channels
     * 4. If native fails, try direct OkHttp as fallback
     */
    public static List<Channel> fetchChannels() {
        List<Channel> channels = new ArrayList<>();

        // Try native library approach first
        if (SplashRTX.isNativeAvailable()) {
            channels = fetchViaNative();
        }

        // If native failed, try direct HTTP approach
        if (channels.isEmpty()) {
            channels = fetchViaHttp();
        }

        // If all failed, return empty (caller will use builtins)
        return channels;
    }

    /**
     * Fetch using native library (bypasses Cloudflare)
     */
    private static List<Channel> fetchViaNative() {
        try {
            SplashRTX splash = new SplashRTX();

            // Step 1: Fetch main config
            String configResponse = splash.performHttpsGet(mConfig.mApiUrl);
            if (configResponse == null || configResponse.isEmpty() || configResponse.contains("<!DOCTYPE")) {
                Log.w(TAG, "Native fetch returned HTML (Cloudflare block), trying API endpoint");
                // Try the API sub-path
                configResponse = splash.performHttpsGet(mConfig.mApiUrl + "api/response_api.php");
            }

            if (configResponse == null || configResponse.isEmpty()) {
                Log.w(TAG, "Native fetch returned empty");
                return new ArrayList<>();
            }

            Log.i(TAG, "Native fetch returned: " + configResponse.substring(0, Math.min(200, configResponse.length())));

            // Step 2: Parse the config response
            return parseConfigAndFetchChannels(configResponse);

        } catch (Exception e) {
            Log.e(TAG, "Native fetch failed: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Fetch using direct HTTP (may be blocked by Cloudflare)
     */
    private static List<Channel> fetchViaHttp() {
        try {
            // Try the response_api.php endpoint directly
            String url = mConfig.mApiUrl + "api/response_api.php";
            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 11; AndroidTV) AppleWebKit/537.36")
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String body = response.body().string();
                    if (!body.contains("<!DOCTYPE") && !body.contains("<html")) {
                        return parseConfigAndFetchChannels(body);
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "HTTP fetch failed: " + e.getMessage());
        }

        // Try DNS endpoint
        try {
            String url = mConfig.mApiUrl + "api/dns.php";
            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 11; AndroidTV) AppleWebKit/537.36")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String body = response.body().string();
                    if (!body.contains("<!DOCTYPE") && !body.contains("<html")) {
                        return parseConfigAndFetchChannels(body);
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "DNS fetch failed: " + e.getMessage());
        }

        return new ArrayList<>();
    }

    /**
     * Parse the config response and fetch channels from the XC server
     * The response can be:
     * - A URL pointing to an XC server
     * - JSON with server URL, username, password
     * - A pipe-delimited string: url|username|password
     */
    private static List<Channel> parseConfigAndFetchChannels(String configResponse) {
        try {
            String serverUrl = null;
            String username = null;
            String password = null;

            String trimmed = configResponse.trim();

            // Try JSON format first
            if (trimmed.startsWith("{")) {
                try {
                    JSONObject json = new JSONObject(trimmed);

                    // Check various JSON formats
                    if (json.has("server_url")) {
                        serverUrl = json.optString("server_url", "");
                        username = json.optString("username", "");
                        password = json.optString("password", "");
                    } else if (json.has("url")) {
                        serverUrl = json.optString("url", "");
                        username = json.optString("user", json.optString("username", ""));
                        password = json.optString("pass", json.optString("password", ""));
                    } else if (json.has("server_info")) {
                        JSONObject serverInfo = json.optJSONObject("server_info");
                        JSONObject userInfo = json.optJSONObject("user_info");
                        if (serverInfo != null) {
                            serverUrl = serverInfo.optString("url", "");
                        }
                        if (userInfo != null) {
                            username = userInfo.optString("username", "");
                            password = userInfo.optString("password", "");
                        }
                    } else if (json.has("user_info") && json.has("server_info")) {
                        // Standard XC login response format
                        JSONObject userInfo = json.getJSONObject("user_info");
                        JSONObject serverInfo = json.getJSONObject("server_info");
                        serverUrl = serverInfo.optString("url", "");
                        username = userInfo.optString("username", "");
                        password = userInfo.optString("password", "");
                    }
                } catch (Exception e) {
                    Log.w(TAG, "JSON parse failed: " + e.getMessage());
                }
            }

            // Try pipe-delimited format: url|username|password
            if (serverUrl == null && trimmed.contains("|")) {
                String[] parts = trimmed.split("\\|");
                if (parts.length >= 3) {
                    serverUrl = parts[0].trim();
                    username = parts[1].trim();
                    password = parts[2].trim();
                }
            }

            // Try newline-delimited format
            if (serverUrl == null && trimmed.contains("\n")) {
                String[] lines = trimmed.split("\n");
                if (lines.length >= 3) {
                    serverUrl = lines[0].trim();
                    username = lines[1].trim();
                    password = lines[2].trim();
                }
            }

            // If it's just a URL (XC server URL without credentials)
            if (serverUrl == null && (trimmed.startsWith("http://") || trimmed.startsWith("https://"))) {
                // The response IS the config - might need to parse differently
                // Try to see if it's a complete XC API response
                if (trimmed.contains("user_info") && trimmed.contains("server_info")) {
                    try {
                        JSONObject json = new JSONObject(trimmed);
                        JSONObject userInfo = json.optJSONObject("user_info");
                        JSONObject serverInfo = json.optJSONObject("server_info");
                        if (userInfo != null && serverInfo != null) {
                            serverUrl = serverInfo.optString("url", "");
                            username = userInfo.optString("username", "");
                            password = userInfo.optString("password", "");
                        }
                    } catch (Exception e) {
                        // Not valid JSON
                    }
                }

                if (serverUrl == null) {
                    // Treat it as a plain URL to an XC server
                    serverUrl = trimmed;
                }
            }

            // If we have XC server details, fetch channels
            if (serverUrl != null && !serverUrl.isEmpty()) {
                Log.i(TAG, "Found XC server: " + serverUrl);
                if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
                    return fetchXCChannels(serverUrl, username, password);
                } else {
                    Log.w(TAG, "XC server found but no credentials");
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Config parse failed: " + e.getMessage());
        }

        return new ArrayList<>();
    }

    /**
     * Fetch live channels from an XC server using the standard API
     */
    private static List<Channel> fetchXCChannels(String serverUrl, String username, String password) {
        List<Channel> channels = new ArrayList<>();

        try {
            // Normalize server URL
            if (!serverUrl.startsWith("http://") && !serverUrl.startsWith("https://")) {
                serverUrl = "http://" + serverUrl;
            }
            String baseUrl = serverUrl.replaceAll("/+$", "");

            // Step 1: Fetch categories
            HashMap<String, String> categoryMap = new HashMap<>();
            try {
                String catUrl = baseUrl + "/player_api.php?username=" + username + "&password=" + password + "&action=get_live_categories";
                Request catRequest = new Request.Builder()
                        .url(catUrl)
                        .header("User-Agent", "Mozilla/5.0")
                        .build();

                try (Response catResponse = client.newCall(catRequest).execute()) {
                    if (catResponse.isSuccessful() && catResponse.body() != null) {
                        JSONArray cats = new JSONArray(catResponse.body().string());
                        for (int i = 0; i < cats.length(); i++) {
                            JSONObject cat = cats.getJSONObject(i);
                            String catId = cat.optString("category_id", "");
                            String catName = cat.optString("category_name", "");
                            categoryMap.put(catId, catName);
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Category fetch failed: " + e.getMessage());
            }

            // Step 2: Fetch live streams
            String streamUrl = baseUrl + "/player_api.php?username=" + username + "&password=" + password + "&action=get_live_streams";
            Request streamRequest = new Request.Builder()
                    .url(streamUrl)
                    .header("User-Agent", "Mozilla/5.0")
                    .build();

            try (Response streamResponse = client.newCall(streamRequest).execute()) {
                if (streamResponse.isSuccessful() && streamResponse.body() != null) {
                    JSONArray streams = new JSONArray(streamResponse.body().string());
                    for (int i = 0; i < streams.length(); i++) {
                        try {
                            JSONObject stream = streams.getJSONObject(i);
                            String name = stream.optString("name", "Unknown");
                            int streamId = stream.optInt("stream_id", 0);
                            String logo = stream.optString("stream_icon", "");
                            String categoryId = stream.optString("category_id", "");

                            // Build stream URL
                            String playUrl = baseUrl + "/live/" + username + "/" + password + "/" + streamId + ".m3u8";

                            // Category
                            String category = categoryMap.containsKey(categoryId) ? categoryMap.get(categoryId) : guessCategory(name);
                            String country = guessCountry(name);
                            String logoKey = guessLogoKey(name);

                            Channel ch = new Channel(i + 1, name, i + 1, category, country,
                                    logoKey, playUrl, logo, name, "infiniti-stream");
                            channels.add(ch);
                        } catch (Exception e) {
                            // Skip malformed entries
                        }
                    }
                }
            }

            Log.i(TAG, "Fetched " + channels.size() + " channels from XC server");

        } catch (Exception e) {
            Log.e(TAG, "XC channel fetch failed: " + e.getMessage());
        }

        return channels;
    }

    // ============================================================
    // Category/Country/Logo guessing helpers
    // ============================================================

    private static String guessCategory(String name) {
        if (name == null) return "Entretenimiento";
        String lower = name.toLowerCase();
        if (lower.contains("sport") || lower.contains("deport") || lower.contains("espn") ||
            lower.contains("fox sport") || lower.contains("tnt sport") || lower.contains("tyc") ||
            lower.contains("futbol") || lower.contains("gol") || lower.contains("win sport") ||
            lower.contains("nba") || lower.contains("nfl") || lower.contains("ufc") ||
            lower.contains("tudn") || lower.contains("dsports") || lower.contains("directv sport") ||
            lower.contains("sportv") || lower.contains("teletrak") || lower.contains("bein") ||
            lower.contains("champion") || lower.contains("ligue") || lower.contains("premier league") ||
            lower.contains("serie a") || lower.contains("bundesliga") || lower.contains("laliga")) {
            return "Deportes";
        }
        if (lower.contains("noticia") || lower.contains("news") || lower.contains("tn") ||
            lower.contains("c5n") || lower.contains("a24") || lower.contains("cronica")) {
            return "Noticias";
        }
        if (lower.contains("pelicula") || lower.contains("movie") || lower.contains("cine") ||
            lower.contains("cinema")) {
            return "Peliculas";
        }
        if (lower.contains("infantil") || lower.contains("kids") || lower.contains("cartoon") ||
            lower.contains("disney") || lower.contains("nick") || lower.contains("nickelodeon")) {
            return "Infantil";
        }
        if (lower.contains("musica") || lower.contains("music") || lower.contains("mtv") ||
            lower.contains("vh1")) {
            return "Musica";
        }
        if (lower.contains("documental") || lower.contains("document") || lower.contains("discov") ||
            lower.contains("nat geo") || lower.contains("history")) {
            return "Documentales";
        }
        return "Entretenimiento";
    }

    private static String guessCountry(String name) {
        if (name == null) return "Latinoamerica";
        String lower = name.toLowerCase();
        if (lower.contains("argentina") || lower.contains("buenos aires")) return "Argentina";
        if (lower.contains("brasil") || lower.contains("globo") || lower.contains("sportv") || lower.contains("band")) return "Brasil";
        if (lower.contains("mexic") || lower.contains("azteca") || lower.contains("tudn")) return "Mexico";
        if (lower.contains("colombia") || lower.contains("win sport") || lower.contains("caracol")) return "Colombia";
        if (lower.contains("chile") || lower.contains("teletrak") || lower.contains("chv") || lower.contains("mega")) return "Chile";
        if (lower.contains("peru") || lower.contains("america tv")) return "Peru";
        if (lower.contains("espan") || lower.contains("spain") || lower.contains("movistar")) return "Espana";
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
        if (lower.contains("tn") && !lower.contains("entreten")) return "tn";
        if (lower.contains("globo")) return "globo";
        if (lower.contains("sportv")) return "sportv";
        if (lower.contains("band")) return "band";
        if (lower.contains("bein")) return "tv";
        if (lower.contains("champion")) return "tv";
        return "tv";
    }
}
