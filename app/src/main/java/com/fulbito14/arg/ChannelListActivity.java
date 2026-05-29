package com.fulbito14.arg;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Channel list screen - shows channels from M3U playlists
 * v2.3: Fixed D-pad navigation, improved focus handling, no category headers breaking nav
 */
public class ChannelListActivity extends Activity {

    private LinearLayout channelContainer;
    private List<Channel> channels;
    private TextView clockText;
    private int focusedIndex = 0;
    private Handler handler = new Handler(Looper.getMainLooper());
    private StringBuilder numberBuffer = new StringBuilder();
    private Runnable numberInputTimeout;
    private View overlayContainer;
    private TextView overlayNumber;
    private ScrollView scrollView;
    private View loadingContainer;
    private ProgressBar loadingBar;
    private TextView loadingText;
    private TextView channelCountText;
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_channel_list);

        channelContainer = findViewById(R.id.channel_container);
        scrollView = findViewById(R.id.scroll_view);
        clockText = findViewById(R.id.text_clock);
        overlayNumber = findViewById(R.id.overlay_number);
        overlayContainer = findViewById(R.id.overlay_container);
        loadingContainer = findViewById(R.id.loading_container);
        loadingBar = findViewById(R.id.loading_bar);
        loadingText = findViewById(R.id.loading_text);
        channelCountText = findViewById(R.id.channel_count);

        String username = getIntent().getStringExtra("username");
        TextView userText = findViewById(R.id.text_user);
        if (userText != null) {
            userText.setText(username != null ? username : "");
        }

        updateClock();

        // Load channels - try cache first, then fetch fresh in background
        channels = ChannelStore.getChannels(this);

        if (channels != null && !channels.isEmpty()) {
            showChannels();
            fetchChannelsInBackground();
        } else {
            showLoading();
            fetchChannelsSync();
        }
    }

    private void showLoading() {
        if (loadingContainer != null) loadingContainer.setVisibility(View.VISIBLE);
        if (scrollView != null) scrollView.setVisibility(View.GONE);
    }

    private void hideLoading() {
        if (loadingContainer != null) loadingContainer.setVisibility(View.GONE);
        if (scrollView != null) scrollView.setVisibility(View.VISIBLE);
    }

    private void fetchChannelsSync() {
        executor.execute(() -> {
            List<Channel> freshChannels = ChannelStore.fetchFreshChannels(this);

            handler.post(() -> {
                if (freshChannels != null && !freshChannels.isEmpty()) {
                    channels = freshChannels;
                    hideLoading();
                    renderChannels();

                    handler.postDelayed(() -> {
                        if (channelContainer.getChildCount() > 0) {
                            setFocus(0);
                        }
                    }, 300);
                } else {
                    channels = ChannelStore.getChannels(this);
                    hideLoading();
                    renderChannels();

                    handler.postDelayed(() -> {
                        if (channelContainer.getChildCount() > 0) {
                            setFocus(0);
                        }
                    }, 300);
                }
            });
        });
    }

    private void fetchChannelsInBackground() {
        executor.execute(() -> {
            List<Channel> freshChannels = ChannelStore.fetchFreshChannels(this);

            handler.post(() -> {
                if (freshChannels != null && !freshChannels.isEmpty() &&
                    (channels == null || freshChannels.size() > channels.size())) {
                    channels = freshChannels;
                    renderChannels();
                }
            });
        });
    }

    private void showChannels() {
        hideLoading();
        renderChannels();

        handler.postDelayed(() -> {
            if (channelContainer.getChildCount() > 0) {
                setFocus(0);
            }
        }, 300);
    }

    private void renderChannels() {
        channelContainer.removeAllViews();
        if (channels == null || channels.isEmpty()) return;

        if (channelCountText != null) {
            channelCountText.setText(channels.size() + " canales");
        }

        LayoutInflater inflater = getLayoutInflater();
        String lastCategory = "";

        for (int i = 0; i < channels.size(); i++) {
            Channel ch = channels.get(i);

            // Add category separator header (NOT focusable, won't break D-pad)
            if (ch.category != null && !ch.category.equals(lastCategory)) {
                lastCategory = ch.category;
                TextView header = new TextView(this);
                header.setText("  " + ch.category.toUpperCase());
                header.setTextColor(Color.parseColor("#FF6600"));
                header.setTextSize(13);
                header.setTypeface(null, android.graphics.Typeface.BOLD);
                header.setPadding(0, 24, 0, 6);
                header.setFocusable(false);
                header.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                channelContainer.addView(header);
            }

            View card = inflater.inflate(R.layout.channel_card, (ViewGroup) channelContainer, false);

            TextView numText = card.findViewById(R.id.card_number);
            TextView nameText = card.findViewById(R.id.card_name);
            TextView catText = card.findViewById(R.id.card_category);
            TextView countryText = card.findViewById(R.id.card_country);
            View logoBg = card.findViewById(R.id.card_logo_bg);
            TextView logoAbbr = card.findViewById(R.id.card_logo_text);
            TextView badge = card.findViewById(R.id.card_badge);

            numText.setText(String.valueOf(ch.number));
            nameText.setText(ch.name);
            catText.setText(ch.category != null ? ch.category : "");
            countryText.setText(ch.country != null ? ch.country : "");

            int bgColor = getLogoColor(ch.logoKey);
            logoBg.setBackgroundColor(bgColor);
            logoAbbr.setText(getLogoAbbr(ch.logoKey));

            // Badge styling
            badge.setVisibility(View.VISIBLE);
            if (ch.isSport()) {
                badge.setText("EN VIVO");
                badge.setBackgroundColor(Color.parseColor("#FF3333"));
                badge.setTextColor(Color.WHITE);
            } else {
                badge.setText("EN VIVO");
                badge.setBackgroundColor(Color.parseColor("#FF3333"));
                badge.setTextColor(Color.WHITE);
            }

            // v2.3: Store channel index directly, use final for lambda
            final int channelIndex = i;
            card.setOnClickListener(v -> playChannel(channelIndex));

            // v2.3: Set up proper D-pad navigation between cards
            card.setNextFocusUpId(View.NO_ID);  // Will be set programmatically
            card.setNextFocusDownId(View.NO_ID);

            channelContainer.addView(card);
        }

        // v2.3: Set up proper focus chain between card views
        setupFocusChain();
    }

    /**
     * v2.3: Build proper D-pad focus chain by linking card views to each other
     * This fixes broken D-pad navigation caused by non-focusable category headers
     */
    private void setupFocusChain() {
        int prevCardId = View.NO_ID;

        for (int i = 0; i < channelContainer.getChildCount(); i++) {
            View child = channelContainer.getChildAt(i);
            if (child.isFocusable()) {
                if (prevCardId != View.NO_ID) {
                    // Link previous card's down to this card
                    View prevCard = channelContainer.findViewById(prevCardId);
                    if (prevCard != null) {
                        prevCard.setNextFocusDownId(child.getId());
                    }
                    // Link this card's up to previous card
                    child.setNextFocusUpId(prevCardId);
                }
                prevCardId = child.getId();
            }
        }
    }

    private int getLogoColor(String key) {
        if (key == null) return Color.parseColor("#884400");
        switch (key) {
            case "espn": return Color.parseColor("#D00000");
            case "dsports": return Color.parseColor("#0066CC");
            case "fox": return Color.parseColor("#1A5276");
            case "tnt": return Color.parseColor("#6C3483");
            case "tyc": return Color.parseColor("#117A65");
            case "win": return Color.parseColor("#D4AC0D");
            case "tudn": return Color.parseColor("#2E86C1");
            case "telefe": return Color.parseColor("#1A8B37");
            case "trece": return Color.parseColor("#C0392B");
            case "america": return Color.parseColor("#2471A3");
            case "canal9": return Color.parseColor("#7D3C98");
            case "publica": return Color.parseColor("#2E86C1");
            case "deportv": return Color.parseColor("#117A65");
            case "tn": return Color.parseColor("#2C3E50");
            case "globo": return Color.parseColor("#1A5276");
            case "sportv": return Color.parseColor("#D00000");
            case "band": return Color.parseColor("#C0392B");
            default: return Color.parseColor("#884400");
        }
    }

    private String getLogoAbbr(String key) {
        if (key == null) return "TV";
        switch (key) {
            case "espn": return "ESPN";
            case "dsports": return "DS";
            case "fox": return "FOX";
            case "tnt": return "TNT";
            case "tyc": return "TyC";
            case "win": return "WIN";
            case "tudn": return "TUDN";
            case "telefe": return "TLF";
            case "trece": return "13";
            case "america": return "AM";
            case "canal9": return "9";
            case "publica": return "TVP";
            case "deportv": return "DTV";
            case "tn": return "TN";
            case "globo": return "GLB";
            case "sportv": return "SPV";
            case "band": return "BND";
            default: return "TV";
        }
    }

    private void setFocus(int index) {
        if (channels == null || channels.isEmpty()) return;
        if (index < 0) index = 0;
        if (index >= channels.size()) index = channels.size() - 1;

        focusedIndex = index;

        // Find and focus the correct card view
        int channelCount = 0;
        for (int i = 0; i < channelContainer.getChildCount(); i++) {
            View child = channelContainer.getChildAt(i);
            if (!child.isFocusable()) continue;

            if (channelCount == index) {
                child.setBackgroundColor(Color.parseColor("#2A1508"));
                child.setTranslationX(10f);
                child.requestFocus();
                if (scrollView != null) {
                    scrollView.smoothScrollTo(0, child.getTop() - 100);
                }
            } else {
                child.setBackgroundColor(Color.parseColor("#141414"));
                child.setTranslationX(0f);
            }
            channelCount++;
        }
    }

    private void playChannel(int index) {
        if (channels == null || index < 0 || index >= channels.size()) return;
        Channel ch = channels.get(index);
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("channel_index", index);
        intent.putExtra("channel_name", ch.name);
        intent.putExtra("channel_number", ch.number);
        intent.putExtra("stream_url", ch.streamUrl);
        intent.putExtra("channel_category", ch.category != null ? ch.category : "");
        startActivity(intent);
    }

    private void updateClock() {
        if (clockText == null) return;
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        clockText.setText(sdf.format(new Date()));
        handler.postDelayed(this::updateClock, 10000);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_0:
            case KeyEvent.KEYCODE_1:
            case KeyEvent.KEYCODE_2:
            case KeyEvent.KEYCODE_3:
            case KeyEvent.KEYCODE_4:
            case KeyEvent.KEYCODE_5:
            case KeyEvent.KEYCODE_6:
            case KeyEvent.KEYCODE_7:
            case KeyEvent.KEYCODE_8:
            case KeyEvent.KEYCODE_9:
                handleNumberInput(keyCode - KeyEvent.KEYCODE_0);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                setFocus(focusedIndex - 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                setFocus(focusedIndex + 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                playChannel(focusedIndex);
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void handleNumberInput(int digit) {
        numberBuffer.append(digit);
        overlayNumber.setText(numberBuffer.toString());
        overlayContainer.setVisibility(View.VISIBLE);

        if (numberInputTimeout != null) {
            handler.removeCallbacks(numberInputTimeout);
        }
        numberInputTimeout = () -> {
            try {
                int chNumber = Integer.parseInt(numberBuffer.toString());
                numberBuffer.setLength(0);
                for (int i = 0; i < channels.size(); i++) {
                    if (channels.get(i).number == chNumber) {
                        playChannel(i);
                        break;
                    }
                }
            } catch (NumberFormatException e) {
                numberBuffer.setLength(0);
            }
            overlayContainer.setVisibility(View.GONE);
        };
        handler.postDelayed(numberInputTimeout, 1500);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (channels != null && focusedIndex >= 0 && focusedIndex < channels.size()) {
            setFocus(focusedIndex);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
    }
}
