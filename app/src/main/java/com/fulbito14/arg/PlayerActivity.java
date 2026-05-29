package com.fulbito14.arg;

import android.app.Activity;
import android.content.Intent;
import android.media.AudioManager;
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
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Player screen - uses ExoPlayer for native HLS playback
 * v1.5 FIX: Now passes Referer header via DefaultHttpDataSource
 * fubohd.com M3U8 streams REQUIRE a Referer header to work (403 without it)
 */
public class PlayerActivity extends Activity {

    private ExoPlayer player;
    private PlayerView playerView;
    private TextView channelName;
    private TextView channelNumber;
    private TextView statusText;
    private ProgressBar progressBar;
    private View overlayControls;
    private View errorView;
    private TextView errorText;
    private TextView errorHint;

    private List<Channel> channels;
    private int currentChannelIndex;
    private Handler handler = new Handler(Looper.getMainLooper());
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private Runnable overlayTimeout;
    private int retryCount = 0;
    private static final int MAX_RETRIES = 3;
    private boolean isDestroyed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        channels = ChannelData.getChannels();
        currentChannelIndex = getIntent().getIntExtra("channel_index", 0);

        playerView = findViewById(R.id.player_view);
        channelName = findViewById(R.id.player_channel_name);
        channelNumber = findViewById(R.id.player_channel_number);
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
        if (index < 0 || index >= channels.size()) return;
        currentChannelIndex = index;
        retryCount = 0;

        Channel ch = channels.get(index);
        channelNumber.setText(String.valueOf(ch.number));
        channelName.setText(ch.name);
        statusText.setText("Conectando...");
        progressBar.setVisibility(View.VISIBLE);
        errorView.setVisibility(View.GONE);
        showOverlay();

        extractAndPlay(ch.embedUrl, ch.embedBackup);
    }

    private void extractAndPlay(String primaryUrl, String backupUrl) {
        executor.execute(() -> {
            // Use the new method that returns the referer
            M3U8Extractor.ExtractResult result = M3U8Extractor.extractM3U8WithReferer(primaryUrl, backupUrl);

            if (result == null) {
                // Try ksdjugfsddeports as additional fallback
                Channel ch = channels.get(currentChannelIndex);
                String ksdSlug = getKsdSlug(ch.name);
                if (ksdSlug != null) {
                    String ksdUrl = "https://deportes.ksdjugfsddeports.com/stream.php?canal=" + ksdSlug + "&target=2";
                    result = M3U8Extractor.extractFromUrlWithReferer(ksdUrl);
                }
            }

            // Try all la14hd.com slugs as final fallback for channels that failed
            if (result == null) {
                Channel ch = channels.get(currentChannelIndex);
                String[] fallbackSlugs = getFallbackSlugs(ch.name);
                for (String slug : fallbackSlugs) {
                    String fbUrl = "https://la14hd.com/vivo/canales.php?stream=" + slug;
                    result = M3U8Extractor.extractFromUrlWithReferer(fbUrl);
                    if (result != null) break;
                }
            }

            final M3U8Extractor.ExtractResult finalResult = result;
            handler.post(() -> {
                if (isDestroyed) return;
                if (finalResult != null) {
                    playStream(finalResult.m3u8Url, finalResult.referer);
                } else {
                    handleError();
                }
            });
        });
    }

    /**
     * Get fallback slugs for channels that might have different names on different sources
     */
    private String[] getFallbackSlugs(String name) {
        if (name == null) return new String[0];
        if (name.contains("Fox Sports 1")) return new String[]{"fox1ar", "foxsports1", "fox1"};
        if (name.contains("Fox Sports 2 AR")) return new String[]{"fox2ar", "foxsports2ar"};
        if (name.contains("DSports 2")) return new String[]{"dsports2", "dsports2ar"};
        if (name.contains("DirecTV Sports")) return new String[]{"directvsports", "directv", "dsports"};
        if (name.contains("TNT Sports Premium")) return new String[]{"tntsportspremium", "tntpremium"};
        if (name.contains("TUDN USA")) return new String[]{"tudn_usa", "tudnusa", "tudn"};
        return new String[0];
    }

    private String getKsdSlug(String name) {
        if (name == null) return null;
        if (name.contains("ESPN Premium")) return "espnpremium";
        if (name.contains("ESPN")) return name.toLowerCase().replace(" ", "").replace("espn", "espn");
        if (name.contains("Fox Sports")) return "foxsports";
        if (name.contains("DSports") || name.contains("DirecTV")) return "directvsports";
        if (name.contains("TNT")) return "tntsports";
        if (name.contains("TyC")) return "tycsports";
        return null;
    }

    /**
     * v1.5 FIX: Now passes Referer header to ExoPlayer via DefaultHttpDataSource
     * This is CRITICAL - fubohd.com streams return 403 without a valid Referer
     */
    private void playStream(String m3u8Url, String referer) {
        if (player == null || isDestroyed) return;

        DefaultHttpDataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Linux; Android 11; AndroidTV) AppleWebKit/537.36")
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(15000);

        // CRITICAL FIX: Set Referer header for all requests made by ExoPlayer
        // This includes the M3U8 playlist AND all TS segment requests
        if (referer != null && !referer.isEmpty()) {
            dataSourceFactory.setDefaultRequestProperties(java.util.Map.of(
                    "Referer", referer,
                    "Origin", referer.endsWith("/") ? referer.substring(0, referer.length() - 1) : referer
            ));
        }

        HlsMediaSource hlsSource = new HlsMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(Uri.parse(m3u8Url)));

        player.setMediaSource(hlsSource);
        player.prepare();
        player.setPlayWhenReady(true);
    }

    private void handleError() {
        retryCount++;
        if (retryCount <= MAX_RETRIES) {
            Channel ch = channels.get(currentChannelIndex);
            statusText.setText("Error - Reintentando...");
            errorView.setVisibility(View.VISIBLE);
            errorText.setText("Error de conexion");
            errorHint.setText("Intentando enlace alternativo (" + retryCount + "/" + MAX_RETRIES + ")...");

            handler.postDelayed(() -> {
                if (!isDestroyed) {
                    // Swap primary/backup on retry
                    extractAndPlay(ch.embedBackup, ch.embedUrl);
                }
            }, 2000);
        } else {
            progressBar.setVisibility(View.GONE);
            errorView.setVisibility(View.VISIBLE);
            errorText.setText("Sin senal");
            errorHint.setText("Presione ATRAS para volver");

            // Don't auto-return, let user try another channel
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
                if (currentChannelIndex > 0) loadChannel(currentChannelIndex - 1);
                showOverlay();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (currentChannelIndex < channels.size() - 1) loadChannel(currentChannelIndex + 1);
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
            case KeyEvent.KEYCODE_0: case KeyEvent.KEYCODE_1: case KeyEvent.KEYCODE_2:
            case KeyEvent.KEYCODE_3: case KeyEvent.KEYCODE_4: case KeyEvent.KEYCODE_5:
            case KeyEvent.KEYCODE_6: case KeyEvent.KEYCODE_7: case KeyEvent.KEYCODE_8:
            case KeyEvent.KEYCODE_9:
                int chNum = keyCode - KeyEvent.KEYCODE_0;
                for (int i = 0; i < channels.size(); i++) {
                    if (channels.get(i).number == chNum) {
                        loadChannel(i);
                        break;
                    }
                }
                showOverlay();
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
        executor.shutdownNow();
        super.onDestroy();
    }
}
