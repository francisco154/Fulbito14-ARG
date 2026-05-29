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
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Player screen - uses ExoPlayer for native HLS/DASH playback
 * v2.3: Fixed Map.of() crash (API 21+), pass data via Intent, improved playback
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
        player = new ExoPlayer.Builder(this)
                .setHandleAudioBecomingNoisy(true)
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

        playStream(ch.streamUrl);
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

        channelNumber.setText(String.valueOf(number));
        channelName.setText(name != null ? name : "Canal");
        if (channelCategory != null) {
            channelCategory.setText(category != null ? category : "");
        }
        statusText.setText("Conectando...");
        progressBar.setVisibility(View.VISIBLE);
        errorView.setVisibility(View.GONE);
        showOverlay();

        if (streamUrl != null && !streamUrl.isEmpty()) {
            playStream(streamUrl);
        } else {
            handleError();
        }
    }

    /**
     * Play a direct M3U8/HLS stream URL
     * v2.3 FIX: Uses HashMap instead of Map.of() for API 21+ compatibility
     */
    private void playStream(String streamUrl) {
        if (player == null || isDestroyed || streamUrl == null || streamUrl.isEmpty()) {
            handleError();
            return;
        }

        try {
            DefaultHttpDataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory()
                    .setUserAgent("Mozilla/5.0 (Linux; Android 11; AndroidTV) AppleWebKit/537.36")
                    .setAllowCrossProtocolRedirects(true)
                    .setConnectTimeoutMs(15000)
                    .setReadTimeoutMs(15000);

            // v2.3 FIX: Use HashMap instead of Map.of() (requires API 24+)
            // Map.of() crashes on Android 5.x/6.x with NoSuchMethodError
            String referer = extractReferer(streamUrl);
            if (referer != null) {
                Map<String, String> headers = new HashMap<>();
                headers.put("Referer", referer);
                headers.put("Origin", referer.endsWith("/") ? referer.substring(0, referer.length() - 1) : referer);
                dataSourceFactory.setDefaultRequestProperties(headers);
            }

            Uri uri = Uri.parse(streamUrl);

            MediaSource mediaSource;

            if (streamUrl.contains(".mpd")) {
                // DASH stream - use DefaultMediaSourceFactory with dataSourceFactory
                DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(this)
                        .setDataSourceFactory(dataSourceFactory);
                mediaSource = mediaSourceFactory.createMediaSource(MediaItem.fromUri(uri));
            } else if (streamUrl.contains(".m3u8") || streamUrl.contains(".m3u") ||
                       streamUrl.contains("/playlist") || streamUrl.contains("/live/")) {
                // HLS stream
                mediaSource = new HlsMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(uri));
            } else {
                // Default: try HLS first (most iptv-org streams are HLS)
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
                        playStream(streamUrl);
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
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }
}
