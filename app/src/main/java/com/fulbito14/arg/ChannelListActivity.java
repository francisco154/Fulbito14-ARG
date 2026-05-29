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
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Channel list screen - shows all available channels
 * v1.5: Improved focus handling, better colors for selected item
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_channel_list);

        channels = ChannelData.getChannels();
        channelContainer = findViewById(R.id.channel_container);
        scrollView = findViewById(R.id.scroll_view);
        clockText = findViewById(R.id.text_clock);
        overlayNumber = findViewById(R.id.overlay_number);
        overlayContainer = findViewById(R.id.overlay_container);

        String username = getIntent().getStringExtra("username");
        TextView userText = findViewById(R.id.text_user);
        if (userText != null) {
            userText.setText(username != null ? username : "");
        }

        renderChannels();
        updateClock();

        handler.postDelayed(() -> {
            if (channelContainer.getChildCount() > 0) {
                setFocus(0);
            }
        }, 300);
    }

    private void renderChannels() {
        channelContainer.removeAllViews();
        LayoutInflater inflater = getLayoutInflater();

        for (int i = 0; i < channels.size(); i++) {
            Channel ch = channels.get(i);
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
            catText.setText(ch.category);
            countryText.setText(ch.country);

            int bgColor = getLogoColor(ch.logoKey);
            logoBg.setBackgroundColor(bgColor);
            logoAbbr.setText(getLogoAbbr(ch.logoKey));

            if (ch.isPremium()) {
                badge.setVisibility(View.VISIBLE);
                badge.setText("PREMIUM");
                badge.setBackgroundColor(Color.parseColor("#FFD700"));
                badge.setTextColor(Color.BLACK);
            } else {
                badge.setVisibility(View.VISIBLE);
                badge.setText("EN VIVO");
                badge.setBackgroundColor(Color.parseColor("#FF3333"));
                badge.setTextColor(Color.WHITE);
            }

            card.setTag(Integer.valueOf(i));
            card.setOnClickListener(v -> {
                int idx = ((Integer) v.getTag()).intValue();
                playChannel(idx);
            });

            channelContainer.addView(card);
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
            default: return "TV";
        }
    }

    private void setFocus(int index) {
        if (index < 0 || index >= channelContainer.getChildCount()) return;
        focusedIndex = index;

        for (int i = 0; i < channelContainer.getChildCount(); i++) {
            View child = channelContainer.getChildAt(i);
            if (i == index) {
                child.setBackgroundColor(Color.parseColor("#2A1508"));
                child.setTranslationX(10f);
            } else {
                child.setBackgroundColor(Color.parseColor("#141414"));
                child.setTranslationX(0f);
            }
        }

        View focused = channelContainer.getChildAt(index);
        if (focused != null) {
            focused.requestFocus();
            scrollView.smoothScrollTo(0, focused.getTop() - 100);
        }
    }

    private void playChannel(int index) {
        Channel ch = channels.get(index);
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("channel_index", index);
        intent.putExtra("channel_name", ch.name);
        intent.putExtra("channel_number", ch.number);
        intent.putExtra("embed_url", ch.embedUrl);
        intent.putExtra("embed_backup", ch.embedBackup);
        startActivity(intent);
    }

    private void updateClock() {
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
            int chNumber = Integer.parseInt(numberBuffer.toString());
            numberBuffer.setLength(0);
            for (int i = 0; i < channels.size(); i++) {
                if (channels.get(i).number == chNumber) {
                    playChannel(i);
                    break;
                }
            }
            overlayContainer.setVisibility(View.GONE);
        };
        handler.postDelayed(numberInputTimeout, 1500);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (focusedIndex >= 0 && focusedIndex < channelContainer.getChildCount()) {
            setFocus(focusedIndex);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
