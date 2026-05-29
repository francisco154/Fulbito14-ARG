package com.fulbito14.arg;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.DefaultLoadControl;

import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Player screen - uses ExoPlayer for native HLS/DASH playback
 * v2.7 BETA 1.3: TELEFE via alsolnet, DeporTV via Videx, TN via vodgc M3U8
 *        High buffer config (50s/60s) for smooth streaming
 *        YouTube Live URL resolver for C5N, LN+
 */
public class PlayerActivity extends Activity {

    private ExoPlayer player;
    private PlayerView playerView;
    private TextView channelName;
    private TextView channelNumber;
    private TextView channelCategory;
    private TextView statusText;
    private ProgressBar progressBar;
    private View overlayControls;
    private View errorView;
    private TextView errorText;
    private TextView errorHint;

    private List<Channel> channels;
    private int currentChannelIndex;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable overlayTimeout;
    private int retryCount = 0;
    private static final int MAX_RETRIES = 2;
    private boolean isDestroyed = false;
    private ExecutorService resolverExecutor = Executors.newSingleThreadExecutor();

    // v2.4: Track current channel's custom user-agent
    private String currentCustomUserAgent = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        // Get channels from ChannelStore (same instance as list)
        channels = ChannelStore.getChannelsSync();
        currentChannelIndex = getIntent().getIntExtra("channel_index", 0);

        playerView = findViewById(R.id.player_view);
        channelName = findViewById(R.id.player_channel_name);
        channelNumber = findViewById(R.id.player_channel_number);
        channelCategory = findViewById(R.id.player_channel_category);
        statusText = findViewById(R.id.player_status);
        progressBar = findViewById(R.id.player_progress);
        overlayControls = findViewById(R.id.player_overlay);
        errorView = findViewById(R.id.player_error);
        errorText = findViewById(R.id.error_text);
        errorHint = findViewById(R.id.error_hint);

        setupPlayer();
        loadChannel(currentChannelIndex);
    }

    private void setupPlayer() {
        // v2.7 BETA 1.3: High buffer config for smooth streaming
        // Buffer up to 60 seconds ahead for smooth playback
        // DefaultLoadControl defaults: minBuffer=50s, maxBuffer=50s
        // We increase maxBuffer to 60s and keep defaults for the rest
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                        50000,   // minBufferMs = 50 seconds
                        60000,   // maxBufferMs = 60 seconds
                        2500,    // bufferForPlaybackMs = 2.5 seconds (start playing quickly)
                        5000     // bufferForPlaybackAfterRebufferMs = 5 seconds
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build();

        player = new ExoPlayer.Builder(this)
                .setHandleAudioBecomingNoisy(true)
                .setLoadControl(loadControl)
                .build();

        playerView.setPlayer(player);

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (isDestroyed) return;
                switch (state) {
                    case Player.STATE_BUFFERING:
                        statusText.setText("Buffering...");
                        progressBar.setVisibility(View.VISIBLE);
                        break;
                    case Player.STATE_READY:
                        statusText.setText("En vivo");
                        progressBar.setVisibility(View.GONE);
                        errorView.setVisibility(View.GONE);
                        showOverlay();
                        break;
                    case Player.STATE_ENDED:
                        statusText.setText("Finalizado");
                        break;
                    case Player.STATE_IDLE:
                        break;
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                if (isDestroyed) return;
                handleError();
            }
        });
    }

    private void loadChannel(int index) {
        if (channels == null || channels.isEmpty()) {
            // Fallback: get channel data from intent extras
            loadChannelFromIntent();
            return;
        }
        if (index < 0 || index >= channels.size()) {
            loadChannelFromIntent();
            return;
        }

        currentChannelIndex = index;
        retryCount = 0;

        Channel ch = channels.get(index);
        channelNumber.setText(String.valueOf(ch.number));
        channelName.setText(ch.name);
        if (channelCategory != null) {
            channelCategory.setText(ch.category != null ? ch.category : "");
        }
        statusText.setText("Conectando...");
        progressBar.setVisibility(View.VISIBLE);
        errorView.setVisibility(View.GONE);
        showOverlay();

        // v2.4: Store custom user-agent from channel
        currentCustomUserAgent = ch.customUserAgent;

        // v2.5: Check if this URL needs resolution (e.g., Telefe API)
        if (needsResolution(ch.streamUrl)) {
            resolveAndPlay(ch.streamUrl, ch.customUserAgent);
        } else {
            playStream(ch.streamUrl, ch.customUserAgent);
        }
    }

    /**
     * Fallback: load channel from intent extras if ChannelStore list doesn't match
     */
    private void loadChannelFromIntent() {
        Intent intent = getIntent();
        String name = intent.getStringExtra("channel_name");
        int number = intent.getIntExtra("channel_number", 1);
        String streamUrl = intent.getStringExtra("stream_url");
        String category = intent.getStringExtra("channel_category");

        // v2.4: Get custom user-agent from intent extras
        String customUA = intent.getStringExtra("custom_user_agent");

        channelNumber.setText(String.valueOf(number));
        channelName.setText(name != null ? name : "Canal");
        if (channelCategory != null) {
            channelCategory.setText(category != null ? category : "");
        }
        statusText.setText("Conectando...");
        progressBar.setVisibility(View.VISIBLE);
        errorView.setVisibility(View.GONE);
        showOverlay();

        // v2.4: Store custom user-agent
        currentCustomUserAgent = customUA;

        if (streamUrl != null && !streamUrl.isEmpty()) {
            if (needsResolution(streamUrl)) {
                resolveAndPlay(streamUrl, customUA);
            } else {
                playStream(streamUrl, customUA);
            }
        } else {
            handleError();
        }
    }

    /**
     * v2.7 BETA 1.2: Check if a URL needs resolution before playback
     * Screenify:// URLs need API resolution to get M3U8
     * Videx:// URLs need proxy resolution to get M3U8
     * YouTube Live URLs need extraction of actual stream URL
     */
    private boolean needsResolution(String url) {
        if (url == null) return false;
        // Screenify protocol - needs API resolution
        if (url.startsWith("screenify://")) return true;
        // Videx protocol - needs proxy resolution
        if (url.startsWith("videx://")) return true;
        // YouTube Live URLs - need stream extraction
        if (url.contains("youtube.com/") || url.contains("youtu.be/")) return true;
        // Any API endpoint that returns redirect/JSON instead of M3U8
        if (url.contains("/Api/") && !url.endsWith(".m3u8") && !url.endsWith(".mpd")) return true;
        return false;
    }

    /**
     * v2.7 BETA 1.2: Resolve an API URL to get the actual stream URL, then play it
     */
    private void resolveAndPlay(String apiUrl, String customUserAgent) {
        statusText.setText("Resolviendo URL...");
        resolverExecutor.execute(() -> {
            String resolvedUrl = resolveStreamUrl(apiUrl);
            handler.post(() -> {
                if (isDestroyed) return;
                if (resolvedUrl != null && !resolvedUrl.isEmpty()) {
                    Log.i("PlayerActivity", "Resolved URL: " + resolvedUrl);
                    playStream(resolvedUrl, customUserAgent);
                } else {
                    // Resolution failed - try playing the URL directly (might redirect)
                    Log.w("PlayerActivity", "URL resolution failed, trying direct play");
                    // For YouTube URLs, try opening in external app
                    if (apiUrl.contains("youtube.com") || apiUrl.contains("youtu.be")) {
                        try {
                            Intent ytIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(apiUrl));
                            ytIntent.setPackage("com.google.android.youtube.tv");
                            startActivity(ytIntent);
                            finish();
                            return;
                        } catch (Exception e) {
                            // YouTube TV not available, try generic
                            try {
                                Intent genericIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(apiUrl));
                                startActivity(genericIntent);
                                finish();
                                return;
                            } catch (Exception e2) {
                                // Can't open externally either
                            }
                        }
                    }
                    playStream(apiUrl, customUserAgent);
                }
            });
        });
    }

    /**
     * v2.7 BETA 1.2: Resolve a stream URL by making an HTTP request
     * Handles Screenify:// protocol, Videx:// proxy, YouTube Live, redirects, JSON responses
     */
    private String resolveStreamUrl(String apiUrl) {
        try {
            // v2.7: Screenify protocol resolution
            // screenify://CONFIG_UUID -> api.screenify.shop -> stream ID -> M3U8
            if (apiUrl.startsWith("screenify://")) {
                return resolveScreenifyUrl(apiUrl);
            }

            // v2.7 BETA 1.2: Videx proxy resolution
            // videx://CHANNEL_PATH -> api.videx.lol/keyvidex.php -> signed M3U8
            if (apiUrl.startsWith("videx://")) {
                return resolveVidexUrl(apiUrl);
            }

            // v2.7 BETA 1.1: YouTube Live URL resolution
            // youtube.com/c/CHANNEL/live or youtube.com/watch?v=ID
            // -> Extract actual HLS stream URL using YouTube page scraping
            if (apiUrl.contains("youtube.com/") || apiUrl.contains("youtu.be/")) {
                return resolveYouTubeLiveUrl(apiUrl);
            }

            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 11; AndroidTV) AppleWebKit/537.36");
            conn.setRequestProperty("Accept", "application/json, application/vnd.apple.mpegurl, */*");

            int responseCode = conn.getResponseCode();
            String contentType = conn.getContentType();
            String finalUrl = conn.getURL().toString();

            // If the final URL is an M3U8/DASH, use it directly
            if (finalUrl.contains(".m3u8") || finalUrl.contains(".mpd") ||
                finalUrl.contains("/playlist") || finalUrl.contains("/index.")) {
                conn.disconnect();
                return finalUrl;
            }

            // If content type is M3U8/HLS, use the URL
            if (contentType != null && (contentType.contains("mpegurl") || contentType.contains("dash"))) {
                conn.disconnect();
                return finalUrl;
            }

            // Try reading the response as text
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
            reader.close();
            conn.disconnect();

            String responseBody = body.toString().trim();

            // Check if response is JSON with a URL
            if (responseBody.startsWith("{")) {
                // Try to extract URL from JSON
                try {
                    org.json.JSONObject json = new org.json.JSONObject(responseBody);
                    // Common JSON fields for stream URLs
                    String[] urlFields = {"url", "sourceUrl", "stream_url", "hls", "src", "file", "source", "link", "playUrl", "video_url"};
                    for (String field : urlFields) {
                        if (json.has(field)) {
                            String extractedUrl = json.optString(field, "");
                            if (!extractedUrl.isEmpty() && extractedUrl.startsWith("http")) {
                                return extractedUrl;
                            }
                        }
                    }
                    // Try nested objects
                    if (json.has("data")) {
                        org.json.JSONObject data = json.optJSONObject("data");
                        if (data != null) {
                            for (String field : urlFields) {
                                if (data.has(field)) {
                                    String extractedUrl = data.optString(field, "");
                                    if (!extractedUrl.isEmpty() && extractedUrl.startsWith("http")) {
                                        return extractedUrl;
                                    }
                                }
                            }
                        }
                    }
                    if (json.has("response")) {
                        org.json.JSONObject resp = json.optJSONObject("response");
                        if (resp != null) {
                            for (String field : urlFields) {
                                if (resp.has(field)) {
                                    String extractedUrl = resp.optString(field, "");
                                    if (!extractedUrl.isEmpty() && extractedUrl.startsWith("http")) {
                                        return extractedUrl;
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.w("PlayerActivity", "JSON parse failed: " + e.getMessage());
                }
            }

            // Check if response itself is an M3U8
            if (responseBody.startsWith("#EXTM3U")) {
                return apiUrl; // The URL itself returns M3U8 content
            }

            // Check if response is a plain URL
            if (responseBody.startsWith("http") && (responseBody.contains(".m3u8") || responseBody.contains(".mpd"))) {
                return responseBody;
            }

            // Fallback: return the final URL after redirects
            return finalUrl;

        } catch (Exception e) {
            Log.e("PlayerActivity", "URL resolution failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * v2.7: Resolve Screenify:// protocol URL to actual M3U8 stream
     * Flow: screenify://UUID -> api.screenify.shop/api/embed-configs/public/UUID
     *       -> get stream number -> 1nyaler.streamhostingcdn.top/stream/NUM/index.m3u8
     */
    private String resolveScreenifyUrl(String screenifyUrl) {
        try {
            // Extract config UUID from screenify://UUID
            String configId = screenifyUrl.substring("screenify://".length());
            String apiUrl = "https://api.screenify.shop/api/embed-configs/public/" + configId;

            Log.i("PlayerActivity", "Resolving Screenify config: " + configId);

            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 11; AndroidTV) AppleWebKit/537.36");
            conn.setRequestProperty("Accept", "application/json");

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                Log.w("PlayerActivity", "Screenify API returned: " + responseCode);
                conn.disconnect();
                return null;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
            reader.close();
            conn.disconnect();

            String responseBody = body.toString().trim();
            Log.i("PlayerActivity", "Screenify response: " + responseBody.substring(0, Math.min(200, responseBody.length())));

            // Parse JSON to extract stream number
            if (responseBody.startsWith("{")) {
                try {
                    org.json.JSONObject json = new org.json.JSONObject(responseBody);
                    String streamId = json.optString("stream", "");
                    if (!streamId.isEmpty()) {
                        String m3u8Url = "https://1nyaler.streamhostingcdn.top/stream/" + streamId + "/index.m3u8";
                        Log.i("PlayerActivity", "Screenify resolved to: " + m3u8Url);
                        return m3u8Url;
                    }
                } catch (Exception e) {
                    Log.e("PlayerActivity", "Screenify JSON parse failed: " + e.getMessage());
                }
            }

            return null;
        } catch (Exception e) {
            Log.e("PlayerActivity", "Screenify resolution failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * v2.7 BETA 1.2: Resolve Videx proxy URL to get actual M3U8 stream
     * Flow: videx://CHANNEL_PATH -> api.videx.lol/keyvidex.php?stream=/CHANNEL_PATH
     *       -> follows redirect -> gets signed M3U8 URL with expiry token
     *       -> returns the signed URL for direct HLS playback
     */
    private String resolveVidexUrl(String videxUrl) {
        try {
            // Extract channel path from videx://CHANNEL_PATH
            String channelPath = videxUrl.substring("videx://".length());
            String apiUrl = "https://api.videx.lol/keyvidex.php?keyvidex=videx&stream=/" + channelPath;

            Log.i("PlayerActivity", "Resolving Videx proxy: " + channelPath);

            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 11; AndroidTV) AppleWebKit/537.36");
            conn.setRequestProperty("Accept", "application/vnd.apple.mpegurl, application/json, */*");

            int responseCode = conn.getResponseCode();
            String finalUrl = conn.getURL().toString();

            // The proxy redirects to a signed M3U8 URL like:
            // https://api.videx.lol/telefe/index.m3u8?expires=...&key=...
            // That URL returns the actual M3U8 playlist content
            if (responseCode == 200) {
                String contentType = conn.getContentType();
                // If we got redirected to an M3U8 URL, return that URL
                if (finalUrl.contains(".m3u8")) {
                    Log.i("PlayerActivity", "Videx resolved to M3U8: " + finalUrl);
                    conn.disconnect();
                    return finalUrl;
                }

                // Read the response body to check for M3U8 content
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder body = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line);
                }
                reader.close();
                conn.disconnect();

                String responseBody = body.toString().trim();

                // If the response is M3U8 content, return the URL (it will be fetched again by player)
                if (responseBody.startsWith("#EXTM3U")) {
                    Log.i("PlayerActivity", "Videx returned M3U8 content, using URL: " + finalUrl);
                    return finalUrl;
                }

                // If response contains a URL with M3U8, extract it
                java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                    "(https?://[^\"\\s\\\\]+\\.m3u8[^\"\\s\\\\]*)"
                ).matcher(responseBody);
                if (m.find()) {
                    String m3u8Url = m.group(1).replace("\\u0026", "&");
                    Log.i("PlayerActivity", "Videx extracted M3U8: " + m3u8Url);
                    return m3u8Url;
                }

                Log.w("PlayerActivity", "Videx response was not M3U8: " + responseBody.substring(0, Math.min(100, responseBody.length())));
            } else {
                Log.w("PlayerActivity", "Videx API returned: " + responseCode);
            }

            conn.disconnect();
            return null;
        } catch (Exception e) {
            Log.e("PlayerActivity", "Videx resolution failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * v2.7 BETA 1.1: Resolve YouTube Live URL to extract the actual stream URL
     * For YouTube /live pages, extracts the video ID from the page HTML
     * Then uses the YouTube embed page to find M3U8 stream URLs
     * Falls back to opening in YouTube TV app if available
     */
    private String resolveYouTubeLiveUrl(String ytUrl) {
        try {
            Log.i("PlayerActivity", "Resolving YouTube Live: " + ytUrl);

            // Step 1: Fetch the YouTube page to find the video ID
            URL url = new URL(ytUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 11; AndroidTV) AppleWebKit/537.36");

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
            reader.close();
            String finalUrl = conn.getURL().toString();
            conn.disconnect();

            String pageContent = body.toString();

            // Step 2: Try to extract video ID from the page
            String videoId = null;

            // Pattern 1: Look for videoId in the page data
            java.util.regex.Matcher m1 = java.util.regex.Pattern.compile("\"videoId\":\"([a-zA-Z0-9_-]{11})\"")
                    .matcher(pageContent);
            if (m1.find()) {
                videoId = m1.group(1);
                Log.i("PlayerActivity", "Found YouTube videoId: " + videoId);
            }

            // Pattern 2: Look for /watch?v= in the final URL
            if (videoId == null) {
                java.util.regex.Matcher m2 = java.util.regex.Pattern.compile("[?&]v=([a-zA-Z0-9_-]{11})")
                        .matcher(finalUrl);
                if (m2.find()) {
                    videoId = m2.group(1);
                    Log.i("PlayerActivity", "Found YouTube videoId from URL: " + videoId);
                }
            }

            // Pattern 3: Look for /embed/ in the page
            if (videoId == null) {
                java.util.regex.Matcher m3 = java.util.regex.Pattern.compile("/embed/([a-zA-Z0-9_-]{11})")
                        .matcher(pageContent);
                if (m3.find()) {
                    videoId = m3.group(1);
                    Log.i("PlayerActivity", "Found YouTube videoId from embed: " + videoId);
                }
            }

            if (videoId != null) {
                // Step 3: Try to get the HLS manifest URL from YouTube
                // We'll try the /get_hls_url approach
                String hlsUrl = tryGetYouTubeHLS(videoId);
                if (hlsUrl != null) {
                    return hlsUrl;
                }

                // If we can't get HLS directly, return null so it falls back to
                // opening in the YouTube TV app
                Log.w("PlayerActivity", "Could not extract HLS, will use YouTube app fallback");
            }

            return null;
        } catch (Exception e) {
            Log.e("PlayerActivity", "YouTube Live resolution failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Try to get HLS manifest URL from YouTube for a given video ID
     * Uses the YouTube player API to extract stream URLs
     */
    private String tryGetYouTubeHLS(String videoId) {
        try {
            // Try fetching the embed page which sometimes has the stream URL
            String embedUrl = "https://www.youtube.com/embed/" + videoId;
            URL url = new URL(embedUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 11; AndroidTV) AppleWebKit/537.36");

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
            reader.close();
            conn.disconnect();

            String pageContent = body.toString();

            // Look for HLS manifest URL in the page
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                    "(https?://[^\"\\\\\\s]+\\.m3u8[^\"\\\\\\s]*)"
            ).matcher(pageContent);
            if (m.find()) {
                String hlsUrl = m.group(1).replace("\\u0026", "&");
                Log.i("PlayerActivity", "Found YouTube HLS URL: " + hlsUrl);
                return hlsUrl;
            }

            return null;
        } catch (Exception e) {
            Log.w("PlayerActivity", "YouTube HLS extraction failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Play a direct M3U8/HLS/DASH stream URL
     * v2.7 BETA 1.1: Improved DASH support with DashMediaSource
     */
    private void playStream(String streamUrl, String customUserAgent) {
        if (player == null || isDestroyed || streamUrl == null || streamUrl.isEmpty()) {
            handleError();
            return;
        }

        try {
            // v2.4: Use custom user-agent if provided, otherwise use default
            String userAgent = (customUserAgent != null && !customUserAgent.isEmpty())
                    ? customUserAgent
                    : "Mozilla/5.0 (Linux; Android 11; AndroidTV) AppleWebKit/537.36";

            DefaultHttpDataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory()
                    .setUserAgent(userAgent)
                    .setAllowCrossProtocolRedirects(true)
                    .setConnectTimeoutMs(15000)
                    .setReadTimeoutMs(15000);

            // v2.3 FIX: Use HashMap instead of Map.of() (requires API 24+)
            String referer = extractReferer(streamUrl);
            if (referer != null) {
                Map<String, String> headers = new HashMap<>();
                headers.put("Referer", referer);
                headers.put("Origin", referer.endsWith("/") ? referer.substring(0, referer.length() - 1) : referer);
                dataSourceFactory.setDefaultRequestProperties(headers);
            }

            Uri uri = Uri.parse(streamUrl);

            MediaSource mediaSource;

            if (streamUrl.contains(".mpd") || streamUrl.contains("index.mpd")) {
                // v2.5: DASH stream - use DashMediaSource for proper DASH playback
                mediaSource = new DashMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(uri));
            } else if (streamUrl.contains(".m3u8") || streamUrl.contains(".m3u") ||
                       streamUrl.contains("/playlist") || streamUrl.contains("/live/")) {
                // HLS stream
                mediaSource = new HlsMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(uri));
            } else {
                // Default: try HLS first (most IPTV streams are HLS)
                mediaSource = new HlsMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(uri));
            }

            player.setMediaSource(mediaSource);
            player.prepare();
            player.setPlayWhenReady(true);

        } catch (Exception e) {
            handleError();
        }
    }

    /**
     * Extract a reasonable Referer from the stream URL
     */
    private String extractReferer(String url) {
        if (url == null) return null;
        try {
            Uri uri = Uri.parse(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme != null && host != null) {
                return scheme + "://" + host + "/";
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    private void handleError() {
        retryCount++;
        if (retryCount <= MAX_RETRIES) {
            statusText.setText("Error - Reintentando...");
            errorView.setVisibility(View.VISIBLE);
            errorText.setText("Error de conexion");
            errorHint.setText("Reintentando (" + retryCount + "/" + MAX_RETRIES + ")...");

            handler.postDelayed(() -> {
                if (!isDestroyed) {
                    String streamUrl = getStreamUrlForCurrentChannel();
                    if (streamUrl != null) {
                        playStream(streamUrl, currentCustomUserAgent);
                    }
                }
            }, 2000);
        } else {
            progressBar.setVisibility(View.GONE);
            errorView.setVisibility(View.VISIBLE);
            errorText.setText("Sin senal");
            errorHint.setText("Presione ATRAS para volver");
        }
    }

    private String getStreamUrlForCurrentChannel() {
        if (channels != null && currentChannelIndex >= 0 && currentChannelIndex < channels.size()) {
            return channels.get(currentChannelIndex).streamUrl;
        }
        return getIntent().getStringExtra("stream_url");
    }

    private void showOverlay() {
        overlayControls.setVisibility(View.VISIBLE);
        if (overlayTimeout != null) handler.removeCallbacks(overlayTimeout);
        overlayTimeout = () -> overlayControls.setVisibility(View.GONE);
        handler.postDelayed(overlayTimeout, 5000);
    }

    private void goBack() {
        if (player != null) {
            player.stop();
            player.clearMediaItems();
        }
        finish();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (channels != null && currentChannelIndex > 0) loadChannel(currentChannelIndex - 1);
                else if (channels == null || channels.isEmpty()) {
                    // Can't navigate without channel list
                }
                showOverlay();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (channels != null && currentChannelIndex < channels.size() - 1) loadChannel(currentChannelIndex + 1);
                showOverlay();
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (player != null) {
                    if (player.isPlaying()) player.pause();
                    else player.play();
                }
                showOverlay();
                return true;
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                goBack();
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) player.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (player != null) player.play();
    }

    @Override
    protected void onDestroy() {
        isDestroyed = true;
        handler.removeCallbacksAndMessages(null);
        resolverExecutor.shutdownNow();
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }
}
