package com.fulbito14.arg;

import android.app.Activity;
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
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;

import java.util.List;

/**
 * Player screen - uses ExoPlayer for native HLS/DASH playback
 * v2.2: Plays direct M3U8 URLs from iptv-org playlists
 * No more embed extraction needed - streams are direct URLs
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
        if (channels == null || index < 0 || index >= channels.size()) return;
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
     * Play a direct M3U8/HLS stream URL
     * v2.2: No more embed extraction - direct stream URLs from M3U playlists
     */
    private void playStream(String streamUrl) {
        if (player == null || isDestroyed || streamUrl == null || streamUrl.isEmpty()) {
            handleError();
            return;
        }

        DefaultHttpDataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Linux; Android 11; AndroidTV) AppleWebKit/537.36")
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(15000);

        // For HLS streams, set Referer to help with some servers
        String referer = extractReferer(streamUrl);
        if (referer != null) {
            dataSourceFactory.setDefaultRequestProperties(java.util.Map.of(
                    "Referer", referer,
                    "Origin", referer.endsWith("/") ? referer.substring(0, referer.length() - 1) : referer
            ));
        }

        Uri uri = Uri.parse(streamUrl);

        if (streamUrl.contains(".m3u8") || streamUrl.contains(".m3u")) {
            // HLS stream
            HlsMediaSource hlsSource = new HlsMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(uri));
            player.setMediaSource(hlsSource);
        } else if (streamUrl.contains(".mpd")) {
            // DASH stream
            DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(this)
                    .setDataSourceFactory(dataSourceFactory);
            player.setMediaSource(mediaSourceFactory.createMediaSource(MediaItem.fromUri(uri)));
        } else {
            // Progressive / other - try HLS first as most iptv-org streams are HLS
            HlsMediaSource hlsSource = new HlsMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(uri));
            player.setMediaSource(hlsSource);
        }

        player.prepare();
        player.setPlayWhenReady(true);
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
                if (!isDestroyed && channels != null && currentChannelIndex < channels.size()) {
                    Channel ch = channels.get(currentChannelIndex);
                    playStream(ch.streamUrl);
                }
            }, 2000);
        } else {
            progressBar.setVisibility(View.GONE);
            errorView.setVisibility(View.VISIBLE);
            errorText.setText("Sin senal");
            errorHint.setText("Presione ATRAS para volver");
        }
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
