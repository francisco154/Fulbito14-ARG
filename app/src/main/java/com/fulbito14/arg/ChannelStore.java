package com.fulbito14.arg;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages channel data - fetches from M3U playlists, caches locally
 * v2.4: Added XC server credentials support, more builtin channels, XC channel merging
 */
public class ChannelStore {

    private static final String PREFS_NAME = "fulbito14_channels";
    private static final String KEY_CACHED_M3U = "cached_m3u_data";
    private static final String KEY_CACHE_TIMESTAMP = "cache_timestamp";
    private static final long CACHE_VALIDITY_MS = 4 * 60 * 60 * 1000; // 4 hours

    // v2.4: XC server credential keys
    private static final String KEY_XC_SERVER = "xc_server_url";
    private static final String KEY_XC_USERNAME = "xc_username";
    private static final String KEY_XC_PASSWORD = "xc_password";

    public static final String USERNAME = "limonsin14";
    public static final String PASSWORD = "1276";

    private static List<Channel> cachedChannels = null;

    /**
     * Get channels - tries cache first, then fetches from network
     */
    public static List<Channel> getChannels(Context context) {
        if (cachedChannels != null && !cachedChannels.isEmpty()) {
            return cachedChannels;
        }

        // Try loading from local cache
        cachedChannels = loadFromCache(context);
        if (cachedChannels != null && !cachedChannels.isEmpty()) {
            return cachedChannels;
        }

        // Fallback: built-in channels (minimal set for offline use)
        cachedChannels = getBuiltinChannels();
        return cachedChannels;
    }

    /**
     * Fetch fresh channels from network (call from background thread)
     * v2.4: Also fetches XC server channels if credentials are saved
     */
    public static List<Channel> fetchFreshChannels(Context context) {
        List<Channel> channels = M3UPlaylistFetcher.fetchAllChannels();

        // v2.4: Merge XC server channels if credentials are available
        if (hasXCCredentials(context)) {
            try {
                String[] creds = getXCCredentials(context);
                List<Channel> xcChannels = XtreamCodesClient.fetchLiveStreams(creds[0], creds[1], creds[2]);
                if (xcChannels != null && !xcChannels.isEmpty()) {
                    // Merge: add XC channels that aren't already in the list
                    java.util.Set<String> seenNames = new java.util.HashSet<>();
                    for (Channel ch : channels) {
                        seenNames.add(ch.name.toLowerCase().replaceAll("[^a-z0-9]", ""));
                    }
                    for (Channel xcCh : xcChannels) {
                        String key = xcCh.name.toLowerCase().replaceAll("[^a-z0-9]", "");
                        if (!seenNames.contains(key)) {
                            seenNames.add(key);
                            channels.add(xcCh);
                        }
                    }
                }
            } catch (Exception e) {
                // XC fetch failed, continue with M3U channels only
            }
        }

        if (channels != null && !channels.isEmpty()) {
            cachedChannels = channels;
            saveToCache(context, channels);
        }

        return channels != null ? channels : new ArrayList<>();
    }

    /**
     * v2.4: Fetch only XC server channels (call from background thread)
     */
    public static List<Channel> fetchXCChannels(Context context) {
        if (!hasXCCredentials(context)) return new ArrayList<>();

        try {
            String[] creds = getXCCredentials(context);
            return XtreamCodesClient.fetchLiveStreams(creds[0], creds[1], creds[2]);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * Get channels synchronously (for quick access, may be empty if not fetched yet)
     */
    public static List<Channel> getChannelsSync() {
        if (cachedChannels != null && !cachedChannels.isEmpty()) {
            return cachedChannels;
        }
        return getBuiltinChannels();
    }

    // ============================================================
    // v2.4: XC Server Credentials Management
    // ============================================================

    /**
     * v2.4: Save XC server credentials
     */
    public static void saveXCCredentials(Context context, String server, String user, String pass) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_XC_SERVER, server)
            .putString(KEY_XC_USERNAME, user)
            .putString(KEY_XC_PASSWORD, pass)
            .apply();
    }

    /**
     * v2.4: Get XC server credentials
     * Returns String[]{serverUrl, username, password}
     */
    public static String[] getXCCredentials(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return new String[]{
            prefs.getString(KEY_XC_SERVER, ""),
            prefs.getString(KEY_XC_USERNAME, ""),
            prefs.getString(KEY_XC_PASSWORD, "")
        };
    }

    /**
     * v2.4: Check if XC credentials are saved
     */
    public static boolean hasXCCredentials(Context context) {
        String[] creds = getXCCredentials(context);
        return !creds[0].isEmpty() && !creds[1].isEmpty() && !creds[2].isEmpty();
    }

    /**
     * v2.4: Clear XC credentials
     */
    public static void clearXCCredentials(Context context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .remove(KEY_XC_SERVER)
            .remove(KEY_XC_USERNAME)
            .remove(KEY_XC_PASSWORD)
            .apply();
    }

    /**
     * v2.4: Test XC server connection (call from background thread)
     */
    public static boolean testXCConnection(String server, String user, String pass) {
        return XtreamCodesClient.testConnection(server, user, pass);
    }

    // ============================================================
    // Cache Management
    // ============================================================

    /**
     * Save fetched channels to SharedPreferences cache
     */
    private static void saveToCache(Context context, List<Channel> channels) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            StringBuilder sb = new StringBuilder();
            for (Channel ch : channels) {
                // Format: name|category|country|logoKey|streamUrl|logoUrl|source|customUserAgent
                sb.append(ch.name).append("|");
                sb.append(ch.category != null ? ch.category : "").append("|");
                sb.append(ch.country != null ? ch.country : "").append("|");
                sb.append(ch.logoKey != null ? ch.logoKey : "").append("|");
                sb.append(ch.streamUrl != null ? ch.streamUrl : "").append("|");
                sb.append(ch.logoUrl != null ? ch.logoUrl : "").append("|");
                sb.append(ch.source != null ? ch.source : "").append("|");
                sb.append(ch.customUserAgent != null ? ch.customUserAgent : "").append("\n");
            }
            prefs.edit()
                 .putString(KEY_CACHED_M3U, sb.toString())
                 .putLong(KEY_CACHE_TIMESTAMP, System.currentTimeMillis())
                 .apply();
        } catch (Exception e) {
            // Ignore cache errors
        }
    }

    /**
     * Load channels from SharedPreferences cache
     */
    private static List<Channel> loadFromCache(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            long timestamp = prefs.getLong(KEY_CACHE_TIMESTAMP, 0);

            // Cache is valid for 4 hours
            if (System.currentTimeMillis() - timestamp > CACHE_VALIDITY_MS) {
                return null;
            }

            String data = prefs.getString(KEY_CACHED_M3U, null);
            if (data == null || data.isEmpty()) return null;

            List<Channel> channels = new ArrayList<>();
            String[] lines = data.split("\n");
            int id = 1;
            for (String line : lines) {
                String[] parts = line.split("\\|", -1);
                if (parts.length >= 5 && !parts[4].isEmpty()) {
                    String name = parts[0];
                    String category = parts[1];
                    String country = parts[2];
                    String logoKey = parts[3];
                    String streamUrl = parts[4];
                    String logoUrl = parts.length > 5 ? parts[5] : "";
                    String source = parts.length > 6 ? parts[6] : "";
                    String customUserAgent = parts.length > 7 ? parts[7] : "";

                    Channel ch = new Channel(id, name, id, category, country, logoKey,
                                           streamUrl, logoUrl, name, source, customUserAgent);
                    channels.add(ch);
                    id++;
                }
            }
            return channels;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Built-in fallback channels for when network is unavailable
     * v2.4: Added DirecTV Sports, Pluto TV, Teletrak, A24, America TV, Bravo TV
     */
    private static List<Channel> getBuiltinChannels() {
        List<Channel> channels = new ArrayList<>();

        channels.add(new Channel(1, "TyC Sports", 1, "Deportes", "Argentina", "tyc",
                "https://amg26268-amg26268c14-freelivesports-emea-10267.playouts.now.amagi.tv/ts-us-e2-n2/playlist/amg26268-sportsstudio-tycsports-freelivesportsemea/playlist.m3u8",
                "", "TyC Sports - Deportes 24hs", "builtin"));

        channels.add(new Channel(2, "DeporTV", 2, "Deportes", "Argentina", "deportv",
                "https://5fb24b460df87.streamlock.net/live-cont.ar/deportv/playlist.m3u8",
                "", "DeporTV - Deporte Publico", "builtin"));

        channels.add(new Channel(3, "Telefe", 3, "Entretenimiento", "Argentina", "telefe",
                "https://live-01-02-telefe.vodgc.net/telefe/index.m3u8",
                "", "Telefe Argentina", "builtin"));

        channels.add(new Channel(4, "El Trece", 4, "Entretenimiento", "Argentina", "trece",
                "https://live-01-02-eltrece.vodgc.net/eltrecetv/index.m3u8",
                "", "El Trece Argentina", "builtin"));

        channels.add(new Channel(5, "Canal 9", 5, "Entretenimiento", "Argentina", "canal9",
                "https://octubre-live.cdn.vustreams.com/live/channel09/live.isml/live.m3u8",
                "", "Canal 9 Argentina", "builtin"));

        channels.add(new Channel(6, "Telemax", 6, "Entretenimiento", "Argentina", "tv",
                "https://live-edge01.telecentro.net.ar/live/smil:tlx.smil/playlist.m3u8",
                "", "Telemax Argentina", "builtin"));

        channels.add(new Channel(7, "FIFA+ Hispanic America", 7, "Deportes", "Latinoamerica", "tv",
                "https://6c849fb3.wurl.com/master/f36d25e7e52f1ba8d7e56eb859c636563214f541/TEctbXhfRklGQVBsdXNTcGFuaXNoLTFfSExT/playlist.m3u8",
                "", "FIFA+ Latinoamerica", "builtin"));

        // v2.4: New builtin channels
        channels.add(new Channel(8, "DirecTV Sports", 8, "Deportes", "Latinoamerica", "dsports",
                "https://latam-cache-sv2-cdn.latamlive.net/DS1_ENC_LIVE/index.mpd",
                "", "DirecTV Sports Latinoamerica", "builtin"));

        channels.add(new Channel(9, "Pluto TV Futbol Para Fans", 9, "Deportes", "Latinoamerica", "tv",
                "https://886bd3fbc782459f8de7555d32d7e9ce.mediatailor.us-west-2.amazonaws.com/v1/master/ba62fe743df0fe93366eba3a257d792884136c7f/LINEAR-957-WORBLATAMESFAST-WHALETVPLUS/957/whaletvplus/hls/master/playlist.m3u8",
                "", "Pluto TV Futbol Para Fans", "builtin"));

        channels.add(new Channel(10, "Teletrak", 10, "Deportes", "Chile", "tv",
                "https://unlimited6-cl.dps.live/sportinghd/sportinghd.smil/playlist.m3u8",
                "", "Teletrak Chile Deportes", "builtin"));

        channels.add(new Channel(11, "A24", 11, "Noticias", "Argentina", "tn",
                "https://g5.vxral-slo.transport.edge-access.net/a12/ngrp:a24-100056_all/playlist.m3u8?sense=true",
                "", "A24 Noticias Argentina", "builtin"));

        channels.add(new Channel(12, "America TV", 12, "Entretenimiento", "Argentina", "america",
                "https://prepublish.f.qaotic.net/a07/americahls-100056/playlist_720p.m3u8",
                "", "America TV Argentina", "builtin"));

        channels.add(new Channel(13, "Bravo TV", 13, "Entretenimiento", "Argentina", "tv",
                "https://redirector.rudo.video/hls-video/c54ac2799874375c81c1672abb700870537c5223/bravo/bravo.smil/playlist.m3u8?did=b2201035844768f58630b7eef",
                "", "Bravo TV Argentina", "builtin"));

        return channels;
    }
}
