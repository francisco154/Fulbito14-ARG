package com.fulbito14.arg;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages channel data - fetches from M3U playlists, caches locally
 * v2.2: Replaces ChannelData (hardcoded) with dynamic M3U playlist fetching
 */
public class ChannelStore {

    private static final String PREFS_NAME = "fulbito14_channels";
    private static final String KEY_CACHED_M3U = "cached_m3u_data";
    private static final String KEY_CACHE_TIMESTAMP = "cache_timestamp";
    private static final long CACHE_VALIDITY_MS = 4 * 60 * 60 * 1000; // 4 hours

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
     */
    public static List<Channel> fetchFreshChannels(Context context) {
        List<Channel> channels = M3UPlaylistFetcher.fetchAllChannels();

        if (channels != null && !channels.isEmpty()) {
            cachedChannels = channels;
            saveToCache(context, channels);
        }

        return channels != null ? channels : new ArrayList<>();
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

    /**
     * Save fetched channels to SharedPreferences cache
     */
    private static void saveToCache(Context context, List<Channel> channels) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            StringBuilder sb = new StringBuilder();
            for (Channel ch : channels) {
                // Format: name|category|country|logoKey|streamUrl|logoUrl|source
                sb.append(ch.name).append("|");
                sb.append(ch.category != null ? ch.category : "").append("|");
                sb.append(ch.country != null ? ch.country : "").append("|");
                sb.append(ch.logoKey != null ? ch.logoKey : "").append("|");
                sb.append(ch.streamUrl != null ? ch.streamUrl : "").append("|");
                sb.append(ch.logoUrl != null ? ch.logoUrl : "").append("|");
                sb.append(ch.source != null ? ch.source : "").append("\n");
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

                    Channel ch = new Channel(id, name, id, category, country, logoKey,
                                           streamUrl, logoUrl, name, source);
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
     * These are known-working M3U8 streams from iptv-org
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

        return channels;
    }
}
