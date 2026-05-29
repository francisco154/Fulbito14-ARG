package com.fulbito14.arg;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

/**
 * Login screen - minimalist and professional
 * v2.5: Simplified - removed XC login mode (no credentials available)
 * Only default login with limonsin14/1276
 */
public class LoginActivity extends Activity {

    private EditText userField;
    private EditText passField;
    private View loginBtn;
    private TextView errorText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        userField = findViewById(R.id.edit_user);
        passField = findViewById(R.id.edit_pass);
        loginBtn = findViewById(R.id.btn_login);
        errorText = findViewById(R.id.text_error);

        loginBtn.setOnClickListener(v -> attemptLogin());
        loginBtn.setFocusable(true);
        loginBtn.setFocusableInTouchMode(true);

        // D-pad navigation chain
        userField.setNextFocusDownId(R.id.edit_pass);
        passField.setNextFocusDownId(R.id.btn_login);
        loginBtn.setNextFocusUpId(R.id.edit_pass);

        userField.requestFocus();

        // Clear error on focus
        View.OnFocusChangeListener clearError = (v, hasFocus) -> {
            if (hasFocus && errorText.getVisibility() == View.VISIBLE) {
                errorText.setVisibility(View.GONE);
            }
        };
        userField.setOnFocusChangeListener(clearError);
        passField.setOnFocusChangeListener(clearError);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            View focused = getCurrentFocus();
            if (focused == userField) {
                passField.requestFocus();
                return true;
            } else if (focused == passField) {
                loginBtn.requestFocus();
                return true;
            } else if (focused == loginBtn) {
                attemptLogin();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private void attemptLogin() {
        String user = userField.getText().toString().trim();
        String pass = passField.getText().toString().trim();

        if (user.isEmpty() || pass.isEmpty()) {
            showError("Complete todos los campos");
            if (user.isEmpty()) userField.requestFocus();
            else passField.requestFocus();
            return;
        }

        if (user.equals(ChannelStore.USERNAME) && pass.equals(ChannelStore.PASSWORD)) {
            errorText.setVisibility(View.GONE);
            Intent intent = new Intent(this, ChannelListActivity.class);
            intent.putExtra("username", user);
            startActivity(intent);
            finish();
        } else {
            showError("Datos incorrectos");
            passField.setText("");
            passField.requestFocus();
        }
    }

    private void showError(String msg) {
        errorText.setText(msg);
        errorText.setTextColor(0xFFFF4444);
        errorText.setVisibility(View.VISIBLE);
    }
}
