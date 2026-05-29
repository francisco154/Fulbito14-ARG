package com.fulbito14.arg;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.rtx.smar4.Config.mConfig;
import com.rtx.smar4.Setting.Prefs;
import com.rtx.smar4.UI.SplashRTX;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages channel data - v2.7 BETA 1.2:
 * ESPN Premium, TNT Sports via Screenify CDN
 * TELEFE via Videx proxy (non-CDN)
 * Canal 9 Litoral, DeporTV, Net TV, Canal E, Bravo TV direct M3U8
 * TV Publica via arcast.com.ar (direct M3U8, no YouTube)
 * A24, TN en Vivo (DASH)
 * NEW: Canal 26, Litus TV, Canal 3 La Pampa, Canal 10 Cordoba
 * Falls back to built-in channels if API is unavailable
 */
public class ChannelStore {

    private static final String PREFS_NAME = "fulbito14_channels";
    private static final String KEY_CACHED_DATA = "cached_channel_data";
    private static final String KEY_CACHE_TIMESTAMP = "cache_timestamp";
    private static final long CACHE_VALIDITY_MS = 4 * 60 * 60 * 1000; // 4 hours

    public static final String USERNAME = "limonsin14";
    public static final String PASSWORD = "1276";

    private static List<Channel> cachedChannels = null;

    /**
     * Get channels - tries memory cache, then disk cache, then builtins
     * This is FAST and never blocks - always returns something
     */
    public static List<Channel> getChannels(Context context) {
        // Memory cache
        if (cachedChannels != null && !cachedChannels.isEmpty()) {
            return cachedChannels;
        }

        // Disk cache
        cachedChannels = loadFromCache(context);
        if (cachedChannels != null && !cachedChannels.isEmpty()) {
            return cachedChannels;
        }

        // Builtins (always available)
        cachedChannels = getBuiltinChannels();
        return cachedChannels;
    }

    /**
     * Get channels synchronously (for quick access)
     */
    public static List<Channel> getChannelsSync() {
        if (cachedChannels != null && !cachedChannels.isEmpty()) {
            return cachedChannels;
        }
        return getBuiltinChannels();
    }

    /**
     * Fetch fresh channels from Infiniti Stream API (call from background thread)
     * v2.5: Uses InfinitiStreamSource instead of M3UPlaylistFetcher
     */
    public static List<Channel> fetchFreshChannels(Context context) {
        Log.i("ChannelStore", "Fetching fresh channels from Infiniti Stream API...");

        // Initialize native library prefs
        try {
            Prefs.initPrefs(context, "vod_info", Context.MODE_PRIVATE);
        } catch (Exception e) {
            // Ignore
        }

        List<Channel> channels = InfinitiStreamSource.fetchChannels();

        if (channels != null && !channels.isEmpty()) {
            // Sort: Sports first, then other categories
            channels.sort((a, b) -> {
                boolean aSport = a.isSport();
                boolean bSport = b.isSport();
                if (aSport && !bSport) return -1;
                if (!aSport && bSport) return 1;
                return a.name.compareToIgnoreCase(b.name);
            });

            // Re-number
            for (int i = 0; i < channels.size(); i++) {
                channels.get(i).number = i + 1;
                channels.get(i).id = i + 1;
            }

            cachedChannels = channels;
            saveToCache(context, channels);
            Log.i("ChannelStore", "Fetched " + channels.size() + " channels from API");
            return channels;
        }

        // API fetch failed - try cache
        List<Channel> cached = loadFromCache(context);
        if (cached != null && !cached.isEmpty()) {
            Log.i("ChannelStore", "Using cached channels (" + cached.size() + ")");
            return cached;
        }

        // Ultimate fallback: builtins
        Log.i("ChannelStore", "Using builtin channels");
        return getBuiltinChannels();
    }

    // ============================================================
    // Cache Management
    // ============================================================

    private static void saveToCache(Context context, List<Channel> channels) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            StringBuilder sb = new StringBuilder();
            for (Channel ch : channels) {
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
                 .putString(KEY_CACHED_DATA, sb.toString())
                 .putLong(KEY_CACHE_TIMESTAMP, System.currentTimeMillis())
                 .apply();
        } catch (Exception e) {
            // Ignore cache errors
        }
    }

    private static List<Channel> loadFromCache(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            long timestamp = prefs.getLong(KEY_CACHE_TIMESTAMP, 0);

            // Cache valid for 4 hours
            if (System.currentTimeMillis() - timestamp > CACHE_VALIDITY_MS) {
                return null;
            }

            String data = prefs.getString(KEY_CACHED_DATA, null);
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
     * Clear the channel cache (forces re-fetch)
     */
    public static void clearCache(Context context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply();
        cachedChannels = null;
    }

    // ============================================================
    // Built-in Fallback Channels - v2.7 BETA 1.2
    // Screenify CDN: ESPN Premium, TNT Sports
    // Videx Proxy: TELEFE (non-CDN)
    // Direct M3U8: Canal 9 Litoral, DeporTV, Net TV, Canal E, Bravo TV
    //              TV Publica (arcast), Canal 26, Litus TV, Canal 3, Canal 10
    // DASH: TN en Vivo
    // YouTube: C5N, LN+ (no direct stream available)
    // ============================================================

    private static List<Channel> getBuiltinChannels() {
        List<Channel> channels = new ArrayList<>();

        // ===================== DEPORTES PREMIUM =====================
        // Screenify CDN - elitegol.vip → mediahosting.space → api.screenify.shop → CDN M3U8

        channels.add(new Channel(1, "ESPN Premium ARG", 1, "Deportes Premium", "Argentina", "espn",
                "screenify://7906e10a-01ab-4ff6-a5e5-9b0d9e447544",
                "", "ESPN Premium Argentina - Screenify CDN", "builtin"));

        channels.add(new Channel(2, "TNT Sports ARG", 2, "Deportes Premium", "Argentina", "tnt",
                "screenify://ba3fa55b-f212-4f69-824c-40e8cef94b56",
                "", "TNT Sports Argentina - Screenify CDN", "builtin"));

        // ===================== DEPORTES =====================

        channels.add(new Channel(3, "TyC Sports", 3, "Deportes", "Argentina", "tyc",
                "https://amg26268-amg26268c14-freelivesports-emea-10267.playouts.now.amagi.tv/ts-us-e2-n2/playlist/amg26268-sportsstudio-tycsports-freelivesportsemea/playlist.m3u8",
                "", "TyC Sports - Deportes 24hs", "builtin"));

        channels.add(new Channel(4, "DeporTV", 4, "Deportes", "Argentina", "deportv",
                "https://5fb24b460df87.streamlock.net/live-cont.ar/deportv/playlist.m3u8",
                "", "DeporTV - Deporte Publico Argentino", "builtin"));

        channels.add(new Channel(5, "FIFA+ Hispanic America", 5, "Deportes", "Latinoamerica", "tv",
                "https://6c849fb3.wurl.com/master/f36d25e7e52f1ba8d7e56eb859c636563214f541/TEctbXhfRklGQVBsdXNTcGFuaXNoLTFfSExT/playlist.m3u8",
                "", "FIFA+ Latinoamerica", "builtin"));

        channels.add(new Channel(6, "Pluto TV Futbol Para Fans", 6, "Deportes", "Latinoamerica", "tv",
                "https://886bd3fbc782459f8de7555d32d7e9ce.mediatailor.us-west-2.amazonaws.com/v1/master/ba62fe743df0fe93366eba3a257d792884136c7f/LINEAR-957-WORBLATAMESFAST-WHALETVPLUS/957/whaletvplus/hls/master/playlist.m3u8",
                "", "Pluto TV Futbol Para Fans", "builtin"));

        channels.add(new Channel(7, "Teletrak", 7, "Deportes", "Chile", "tv",
                "https://unlimited6-cl.dps.live/sportinghd/sportinghd.smil/playlist.m3u8",
                "", "Teletrak Chile Deportes", "builtin"));

        // ===================== ENTRETENIMIENTO =====================

        channels.add(new Channel(8, "El Trece", 8, "Entretenimiento", "Argentina", "trece",
                "https://livetrx01.vodgc.net/eltrecetv/index.m3u8",
                "", "El Trece Argentina", "builtin"));

        // BETA 1.2: TELEFE via Videx proxy (non-CDN, direct M3U8 resolution)
        channels.add(new Channel(9, "TELEFE", 9, "Entretenimiento", "Argentina", "telefe",
                "videx://telefe/index.m3u8",
                "", "Telefe Argentina - Videx Proxy", "builtin"));

        channels.add(new Channel(10, "Canal 9", 10, "Entretenimiento", "Argentina", "canal9",
                "https://stream.arcast.live/ahora/ahora/playlist.m3u8",
                "", "Canal 9 Litoral Argentina", "builtin"));

        channels.add(new Channel(11, "America TV", 11, "Entretenimiento", "Argentina", "america",
                "https://prepublish.f.qaotic.net/a07/americahls-100056/playlist_720p.m3u8",
                "", "America TV Argentina", "builtin"));

        channels.add(new Channel(12, "Bravo TV", 12, "Entretenimiento", "Argentina", "tv",
                "https://redirector.rudo.video/hls-video/c54ac2799874375c81c1672abb700870537c5223/bravo/bravo.smil/playlist.m3u8?did=b2201035844768f58630b7eef",
                "", "Bravo TV Argentina", "builtin"));

        channels.add(new Channel(13, "Telemax", 13, "Entretenimiento", "Argentina", "tv",
                "https://stream-gtlc.telecentro.net.ar/hls/telemaxhls/main.m3u8",
                "", "Telemax Argentina", "builtin"));

        channels.add(new Channel(14, "Net TV", 14, "Entretenimiento", "Argentina", "tv",
                "https://unlimited1-us.dps.live/nettv/nettv.smil/playlist.m3u8",
                "", "Net TV Argentina", "builtin"));

        channels.add(new Channel(15, "Canal E", 15, "Entretenimiento", "Argentina", "tv",
                "https://unlimited1-us.dps.live/perfiltv/perfiltv.smil/perfiltv/livestream2/chunks.m3u8",
                "", "Canal E Argentina", "builtin"));

        // ===================== NOTICIAS =====================

        channels.add(new Channel(16, "A24", 16, "Noticias", "Argentina", "tn",
                "https://g5.vxral-slo.transport.edge-access.net/a12/ngrp:a24-100056_all/playlist.m3u8?sense=true",
                "", "A24 Noticias Argentina", "builtin"));

        channels.add(new Channel(17, "TN en Vivo", 17, "Noticias", "Argentina", "tn",
                "https://latam-cache-sv2-cdn.latamlive.net/TN_ENC_LIVE/index.mpd",
                "", "Todo Noticias Argentina", "builtin"));

        // BETA 1.2: TV Publica via arcast.com.ar - direct M3U8 (no YouTube)
        channels.add(new Channel(18, "TV Publica", 18, "Noticias", "Argentina", "publica",
                "https://stream.arcast.com.ar/envivo/castv/playlist.m3u8",
                "", "Television Publica Argentina - Arcast", "builtin"));

        // C5N and LN+: No direct non-YouTube streams available
        // These channels have locked streams behind DRM/geo-blocked CDNs
        channels.add(new Channel(19, "C5N", 19, "Noticias", "Argentina", "tn",
                "https://www.youtube.com/c/c5n/live",
                "", "C5N Noticias Argentina", "builtin"));

        channels.add(new Channel(20, "LN+", 20, "Noticias", "Argentina", "tn",
                "https://www.youtube.com/c/LaNacionMas/live",
                "", "La Nacion Mas Argentina", "builtin"));

        // ===================== CANALES REGIONALES =====================

        channels.add(new Channel(21, "Canal 26", 21, "Regionales", "Argentina", "tv",
                "https://stream-gtlc.telecentro.net.ar/hls/canal26hls/main.m3u8",
                "", "Canal 26 Buenos Aires", "builtin"));

        channels.add(new Channel(22, "Litus TV", 22, "Regionales", "Argentina", "tv",
                "https://stream.arcast.com.ar/litustv/ngrp:litustv_all/playlist.m3u8",
                "", "Litus TV Argentina", "builtin"));

        channels.add(new Channel(23, "Canal 3 La Pampa", 23, "Regionales", "Argentina", "tv",
                "https://stream.arcast.com.ar/c3lapampa/ngrp:c3lapampa_all/playlist.m3u8",
                "", "Canal 3 La Pampa Argentina", "builtin"));

        channels.add(new Channel(24, "Canal 10 Cordoba", 24, "Regionales", "Argentina", "tv",
                "https://stream.arcast.net:4443/canal10/ngrp:canal10_all/playlist.m3u8",
                "", "Canal 10 Cordoba Argentina", "builtin"));

        return channels;
    }
}
