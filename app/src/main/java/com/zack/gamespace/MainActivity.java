package com.zack.gamespace;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.pm.ServiceInfo;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import java.util.List;

public class MainActivity extends AppCompatActivity implements AutoTapService.StateListener {

    private static final int INTERVAL_STEP_MS = 10;
    private static final int INTERVAL_SEEK_MAX =
            (TapSettings.MAX_INTERVAL_MS - TapSettings.MIN_INTERVAL_MS) / INTERVAL_STEP_MS;
    private static final int HOLD_SEEK_MAX = TapSettings.MAX_HOLD_MS - TapSettings.MIN_HOLD_MS;

    private TapSettings settings;

    private TextView titleView;
    private View statusDot;
    private TextView statusText;
    private TextView statusHint;
    private TextView btnService;
    private TextView intervalLabel;
    private TextView holdLabel;
    private SeekBar intervalSeek;
    private SeekBar holdSeek;
    private EditText repeatInput;
    private SwitchCompat jitterSwitch;
    private TextView btnOverlay;
    private TextView btnStop;
    private TextView btnLibrary;

    private ObjectAnimator pulseAnimator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        settings = new TapSettings(this);

        titleView = findViewById(R.id.tv_title);
        statusDot = findViewById(R.id.status_dot);
        statusText = findViewById(R.id.tv_status);
        statusHint = findViewById(R.id.tv_status_hint);
        btnService = findViewById(R.id.btn_service);
        intervalLabel = findViewById(R.id.tv_interval_label);
        holdLabel = findViewById(R.id.tv_hold_label);
        intervalSeek = findViewById(R.id.seek_interval);
        holdSeek = findViewById(R.id.seek_hold);
        repeatInput = findViewById(R.id.et_repeat);
        jitterSwitch = findViewById(R.id.switch_jitter);
        btnOverlay = findViewById(R.id.btn_overlay);
        btnStop = findViewById(R.id.btn_stop);
        btnLibrary = findViewById(R.id.btn_library);

        applyGradientTitle();
        bindSettings();
        bindActions();

        pulseAnimator = ObjectAnimator.ofFloat(statusDot, View.ALPHA, 1f, 0.3f);
        pulseAnimator.setDuration(900);
        pulseAnimator.setRepeatCount(ObjectAnimator.INFINITE);
        pulseAnimator.setRepeatMode(ObjectAnimator.REVERSE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        AutoTapService.setStateListener(this);
        pulseAnimator.start();
        refreshUi();
    }

    @Override
    protected void onPause() {
        super.onPause();
        AutoTapService.setStateListener(null);
        pulseAnimator.cancel();
        statusDot.setAlpha(1f);
    }

    @Override
    public void onServiceStateChanged() {
        refreshUi();
    }

    // ----- Pengikatan UI -----

    private void applyGradientTitle() {
        String text = titleView.getText().toString();
        float width = titleView.getPaint().measureText(text);
        Shader shader = new LinearGradient(0f, 0f, width, 0f,
                new int[]{getColor(R.color.neon_cyan), getColor(R.color.neon_violet), getColor(R.color.neon_purple)},
                null, Shader.TileMode.CLAMP);
        titleView.getPaint().setShader(shader);
        titleView.invalidate();
    }

    private void bindSettings() {
        int interval = settings.getIntervalMs();
        intervalSeek.setMax(INTERVAL_SEEK_MAX);
        intervalSeek.setProgress((interval - TapSettings.MIN_INTERVAL_MS) / INTERVAL_STEP_MS);
        updateIntervalLabel(interval);
        intervalSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = TapSettings.MIN_INTERVAL_MS + progress * INTERVAL_STEP_MS;
                updateIntervalLabel(value);
                if (fromUser) {
                    settings.setIntervalMs(value);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        int hold = settings.getHoldMs();
        holdSeek.setMax(HOLD_SEEK_MAX);
        holdSeek.setProgress(hold - TapSettings.MIN_HOLD_MS);
        updateHoldLabel(hold);
        holdSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = TapSettings.MIN_HOLD_MS + progress;
                updateHoldLabel(value);
                if (fromUser) {
                    settings.setHoldMs(value);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        int repeat = settings.getRepeatCount();
        repeatInput.setText(repeat == 0 ? "" : String.valueOf(repeat));
        repeatInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                int value = 0;
                try {
                    String raw = s.toString().trim();
                    if (!raw.isEmpty()) {
                        value = Integer.parseInt(raw);
                    }
                } catch (NumberFormatException ignored) {
                    value = 0;
                }
                settings.setRepeatCount(value);
            }
        });

        jitterSwitch.setChecked(settings.isJitter());
        jitterSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> settings.setJitter(isChecked));
    }

    private void bindActions() {
        btnService.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        btnOverlay.setOnClickListener(v -> {
            AutoTapService service = AutoTapService.getInstance();
            if (service == null) {
                toast(R.string.toast_need_service);
                return;
            }
            if (service.isOverlayShowing()) {
                service.hideOverlay();
            } else if (service.showOverlay()) {
                toast(R.string.toast_overlay_shown);
            } else {
                toast(R.string.toast_overlay_failed);
            }
            refreshUi();
        });

        btnStop.setOnClickListener(v -> {
            AutoTapService service = AutoTapService.getInstance();
            if (service == null) {
                toast(R.string.toast_need_service);
                return;
            }
            service.stopTapping();
            refreshUi();
        });

        btnLibrary.setOnClickListener(v ->
                startActivity(new Intent(this, GameLibraryActivity.class)));
    }

    private void updateIntervalLabel(int ms) {
        intervalLabel.setText(getString(R.string.label_interval, ms, 1000f / ms));
    }

    private void updateHoldLabel(int ms) {
        holdLabel.setText(getString(R.string.label_hold, ms));
    }

    // ----- Status -----

    private void refreshUi() {
        AutoTapService service = AutoTapService.getInstance();
        boolean connected = service != null;
        boolean enabled = connected || isServiceEnabled();

        int color;
        int statusRes;
        int hintRes;
        if (connected && service.isRunning()) {
            color = getColor(R.color.neon_cyan);
            statusRes = R.string.status_running;
            hintRes = R.string.status_hint_running;
        } else if (connected) {
            color = getColor(R.color.neon_blue);
            statusRes = R.string.status_ready;
            hintRes = R.string.status_hint_ready;
        } else if (enabled) {
            color = getColor(R.color.neon_violet);
            statusRes = R.string.status_waiting;
            hintRes = R.string.status_hint_waiting;
        } else {
            color = getColor(R.color.neon_alert);
            statusRes = R.string.status_off;
            hintRes = R.string.status_hint_off;
        }
        statusDot.setBackgroundTintList(ColorStateList.valueOf(color));
        statusText.setTextColor(color);
        statusText.setText(statusRes);
        if (connected && service.isRunning()) {
            statusHint.setText(getString(hintRes, service.getTapCount()));
        } else {
            statusHint.setText(hintRes);
        }

        btnService.setText(connected ? R.string.btn_service_manage : R.string.btn_service_enable);
        btnOverlay.setText(connected && service.isOverlayShowing()
                ? R.string.btn_overlay_hide : R.string.btn_overlay_show);
        btnOverlay.setAlpha(connected ? 1f : 0.45f);
        btnStop.setAlpha(connected ? 1f : 0.45f);
    }

    private boolean isServiceEnabled() {
        AccessibilityManager manager = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        if (manager == null) {
            return false;
        }
        List<AccessibilityServiceInfo> list =
                manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        for (AccessibilityServiceInfo info : list) {
            ServiceInfo serviceInfo = info.getResolveInfo().serviceInfo;
            if (serviceInfo.packageName.equals(getPackageName())
                    && serviceInfo.name.equals(AutoTapService.class.getName())) {
                return true;
            }
        }
        return false;
    }

    private void toast(int resId) {
        Toast.makeText(this, resId, Toast.LENGTH_LONG).show();
    }
}
