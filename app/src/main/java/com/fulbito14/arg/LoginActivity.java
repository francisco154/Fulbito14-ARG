package com.fulbito14.arg;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Login screen - authenticates with hardcoded credentials
 * v1.5 FIX: Improved D-pad focus handling, auto-fill support, better error messages
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

        // Make login button focusable for D-pad
        loginBtn.setNextFocusUpId(R.id.edit_pass);
        loginBtn.setNextFocusDownId(R.id.edit_user);
        passField.setNextFocusDownId(R.id.btn_login);
        passField.setNextFocusForwardId(R.id.btn_login);

        userField.requestFocus();

        // Clear error when typing
        userField.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) errorText.setVisibility(View.GONE);
        });
        passField.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) errorText.setVisibility(View.GONE);
        });
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
        // Handle TAB key for D-pad navigation
        if (keyCode == KeyEvent.KEYCODE_TAB) {
            View focused = getCurrentFocus();
            if (focused == userField) {
                passField.requestFocus();
                return true;
            } else if (focused == passField) {
                loginBtn.requestFocus();
                return true;
            } else {
                userField.requestFocus();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private void attemptLogin() {
        String user = userField.getText().toString().trim();
        String pass = passField.getText().toString().trim();

        if (user.isEmpty() || pass.isEmpty()) {
            errorText.setVisibility(View.VISIBLE);
            errorText.setText("Ingrese usuario y contrasena");
            if (user.isEmpty()) userField.requestFocus();
            else passField.requestFocus();
            return;
        }

        if (user.equals(ChannelData.USERNAME) && pass.equals(ChannelData.PASSWORD)) {
            errorText.setVisibility(View.GONE);
            Intent intent = new Intent(this, ChannelListActivity.class);
            intent.putExtra("username", user);
            startActivity(intent);
            finish();
        } else {
            errorText.setVisibility(View.VISIBLE);
            errorText.setText("Usuario o contrasena incorrectos");
            userField.setText("");
            passField.setText("");
            userField.requestFocus();
        }
    }
}
