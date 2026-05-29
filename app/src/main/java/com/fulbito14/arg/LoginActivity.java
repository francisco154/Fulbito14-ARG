package com.fulbito14.arg;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Login screen - minimalist and professional
 * v2.4: Added XC server login mode (Xtream Codes), toggle between default and XC login
 */
public class LoginActivity extends Activity {

    private EditText userField;
    private EditText passField;
    private View loginBtn;
    private TextView errorText;
    private ProgressBar loginProgress;

    // v2.4: XC server login fields
    private View toggleModeBtn;
    private View defaultLoginContainer;
    private View xcLoginContainer;
    private EditText xcServerField;
    private EditText xcUserField;
    private EditText xcPassField;

    private boolean isXCMode = false;
    private Handler handler = new Handler(Looper.getMainLooper());
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        userField = findViewById(R.id.edit_user);
        passField = findViewById(R.id.edit_pass);
        loginBtn = findViewById(R.id.btn_login);
        errorText = findViewById(R.id.text_error);
        loginProgress = findViewById(R.id.login_progress);

        // v2.4: XC mode views
        toggleModeBtn = findViewById(R.id.btn_toggle_mode);
        defaultLoginContainer = findViewById(R.id.default_login_container);
        xcLoginContainer = findViewById(R.id.xc_login_container);
        xcServerField = findViewById(R.id.edit_xc_server);
        xcUserField = findViewById(R.id.edit_xc_user);
        xcPassField = findViewById(R.id.edit_xc_pass);

        loginBtn.setOnClickListener(v -> attemptLogin());
        loginBtn.setFocusable(true);
        loginBtn.setFocusableInTouchMode(true);

        // v2.4: Toggle between default and XC login modes
        toggleModeBtn.setOnClickListener(v -> toggleLoginMode());
        toggleModeBtn.setFocusable(true);
        toggleModeBtn.setFocusableInTouchMode(true);

        // D-pad navigation chain (default mode)
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
        if (xcServerField != null) xcServerField.setOnFocusChangeListener(clearError);
        if (xcUserField != null) xcUserField.setOnFocusChangeListener(clearError);
        if (xcPassField != null) xcPassField.setOnFocusChangeListener(clearError);

        // v2.4: Pre-fill XC fields if credentials are saved
        if (ChannelStore.hasXCCredentials(this)) {
            String[] creds = ChannelStore.getXCCredentials(this);
            if (xcServerField != null) xcServerField.setText(creds[0]);
            if (xcUserField != null) xcUserField.setText(creds[1]);
            if (xcPassField != null) xcPassField.setText(creds[2]);
        }
    }

    /**
     * v2.4: Toggle between default login and XC server login
     */
    private void toggleLoginMode() {
        isXCMode = !isXCMode;
        errorText.setVisibility(View.GONE);

        if (isXCMode) {
            defaultLoginContainer.setVisibility(View.GONE);
            xcLoginContainer.setVisibility(View.VISIBLE);
            ((TextView) toggleModeBtn).setText("Ingresar con usuario y contraseña");
            // Update D-pad navigation for XC mode
            if (xcServerField != null) xcServerField.requestFocus();
        } else {
            defaultLoginContainer.setVisibility(View.VISIBLE);
            xcLoginContainer.setVisibility(View.GONE);
            ((TextView) toggleModeBtn).setText("Ingresar con servidor IPTV (Xtream Codes)");
            userField.requestFocus();
        }

        updateNavigationChain();
    }

    /**
     * v2.4: Update D-pad focus chain based on current mode
     */
    private void updateNavigationChain() {
        if (isXCMode) {
            if (xcServerField != null) xcServerField.setNextFocusDownId(R.id.edit_xc_user);
            if (xcUserField != null) xcUserField.setNextFocusDownId(R.id.edit_xc_pass);
            if (xcPassField != null) xcPassField.setNextFocusDownId(R.id.btn_login);
            loginBtn.setNextFocusUpId(R.id.edit_xc_pass);
        } else {
            userField.setNextFocusDownId(R.id.edit_pass);
            passField.setNextFocusDownId(R.id.btn_login);
            loginBtn.setNextFocusUpId(R.id.edit_pass);
        }
        toggleModeBtn.setNextFocusDownId(isXCMode ? R.id.edit_xc_server : R.id.edit_user);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            View focused = getCurrentFocus();

            if (focused == toggleModeBtn) {
                toggleLoginMode();
                return true;
            }

            if (!isXCMode) {
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
            } else {
                if (focused == xcServerField) {
                    if (xcUserField != null) xcUserField.requestFocus();
                    return true;
                } else if (focused == xcUserField) {
                    if (xcPassField != null) xcPassField.requestFocus();
                    return true;
                } else if (focused == xcPassField) {
                    loginBtn.requestFocus();
                    return true;
                } else if (focused == loginBtn) {
                    attemptLogin();
                    return true;
                }
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private void attemptLogin() {
        if (isXCMode) {
            attemptXCLogin();
        } else {
            attemptDefaultLogin();
        }
    }

    /**
     * Default login with username/password
     */
    private void attemptDefaultLogin() {
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

    /**
     * v2.4: XC server login - test connection then proceed
     */
    private void attemptXCLogin() {
        String server = xcServerField != null ? xcServerField.getText().toString().trim() : "";
        String user = xcUserField != null ? xcUserField.getText().toString().trim() : "";
        String pass = xcPassField != null ? xcPassField.getText().toString().trim() : "";

        if (server.isEmpty() || user.isEmpty() || pass.isEmpty()) {
            showError("Complete todos los campos");
            if (server.isEmpty() && xcServerField != null) xcServerField.requestFocus();
            else if (user.isEmpty() && xcUserField != null) xcUserField.requestFocus();
            else if (xcPassField != null) xcPassField.requestFocus();
            return;
        }

        // Ensure URL has protocol
        if (!server.startsWith("http://") && !server.startsWith("https://")) {
            server = "http://" + server;
            if (xcServerField != null) xcServerField.setText(server);
        }

        // Show progress, disable login button
        showError("Conectando al servidor...");
        errorText.setTextColor(0xFFAAAAAA);
        errorText.setVisibility(View.VISIBLE);
        loginBtn.setEnabled(false);
        if (loginProgress != null) loginProgress.setVisibility(View.VISIBLE);

        final String finalServer = server;
        final String finalUser = user;
        final String finalPass = pass;

        executor.execute(() -> {
            boolean success = ChannelStore.testXCConnection(finalServer, finalUser, finalPass);

            handler.post(() -> {
                loginBtn.setEnabled(true);
                if (loginProgress != null) loginProgress.setVisibility(View.GONE);
                errorText.setTextColor(0xFFFF4444);

                if (success) {
                    // Save XC credentials
                    ChannelStore.saveXCCredentials(LoginActivity.this, finalServer, finalUser, finalPass);
                    errorText.setVisibility(View.GONE);

                    Intent intent = new Intent(LoginActivity.this, ChannelListActivity.class);
                    intent.putExtra("username", finalUser);
                    intent.putExtra("xc_mode", true);
                    startActivity(intent);
                    finish();
                } else {
                    showError("No se pudo conectar al servidor. Verifique los datos.");
                }
            });
        });
    }

    private void showError(String msg) {
        errorText.setText(msg);
        errorText.setTextColor(0xFFFF4444);
        errorText.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
    }
}
