package com.zack.gamespace;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;

import java.util.Random;

/**
 * Layanan aksesibilitas yang melakukan tap berulang lewat dispatchGesture().
 * Layanan ini TIDAK membaca isi layar; ia hanya menyuntikkan tap pada titik target.
 */
public class AutoTapService extends AccessibilityService implements OverlayController.Listener {

    /** Dipanggil di main thread setiap status layanan/auto tap berubah. */
    public interface StateListener {
        void onServiceStateChanged();
    }

    private static final int MAX_CONSECUTIVE_FAILURES = 5;

    private static volatile AutoTapService instance;
    private static volatile StateListener stateListener;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    private TapSettings settings;
    private OverlayController overlay;

    private boolean running;
    private long tapCount;
    private int consecutiveFailures;
    private long lastDispatchAt;

    // Snapshot pengaturan saat auto tap dimulai
    private int targetX;
    private int targetY;
    private int intervalMs;
    private int holdMs;
    private int repeatLimit;
    private boolean jitter;

    public static AutoTapService getInstance() {
        return instance;
    }

    public static void setStateListener(StateListener listener) {
        stateListener = listener;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        settings = new TapSettings(this);
        overlay = new OverlayController(this, settings, this);
        notifyState();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Sengaja kosong: layanan tidak membaca konten layar.
    }

    @Override
    public void onInterrupt() {
        stopTapping();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        shutdown();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        shutdown();
        super.onDestroy();
    }

    private void shutdown() {
        stopTapping();
        if (overlay != null) {
            overlay.hide();
        }
        if (instance == this) {
            instance = null;
        }
        notifyState();
    }

    // ----- API publik untuk MainActivity / GameLibraryActivity -----

    public boolean isRunning() {
        return running;
    }

    public long getTapCount() {
        return tapCount;
    }

    public boolean isOverlayShowing() {
        return overlay != null && overlay.isShowing();
    }

    public boolean showOverlay() {
        boolean shown = overlay != null && overlay.show();
        notifyState();
        return shown;
    }

    public void hideOverlay() {
        stopTapping();
        if (overlay != null) {
            overlay.hide();
        }
        notifyState();
    }

    public void startTapping() {
        if (running || overlay == null) {
            return;
        }
        int[] center = overlay.getTargetCenter();
        targetX = center[0];
        targetY = center[1];
        settings.setTarget(targetX, targetY);

        intervalMs = settings.getIntervalMs();
        holdMs = settings.getHoldMs();
        repeatLimit = settings.getRepeatCount();
        jitter = settings.isJitter();

        running = true;
        tapCount = 0;
        consecutiveFailures = 0;
        overlay.setRunning(true);
        notifyState();
        handler.post(tapRunnable);
    }

    public void stopTapping() {
        boolean wasRunning = running;
        running = false;
        handler.removeCallbacks(tapRunnable);
        if (overlay != null) {
            overlay.setRunning(false);
        }
        if (wasRunning) {
            notifyState();
        }
    }

    // ----- OverlayController.Listener -----

    @Override
    public void onToggleRequested() {
        if (running) {
            stopTapping();
        } else {
            startTapping();
        }
    }

    @Override
    public void onCloseRequested() {
        hideOverlay();
    }

    // ----- Mesin tap -----

    private final Runnable tapRunnable = new Runnable() {
        @Override
        public void run() {
            performTap();
        }
    };

    private final GestureResultCallback gestureCallback = new GestureResultCallback() {
        @Override
        public void onCompleted(GestureDescription gestureDescription) {
            if (!running) {
                return;
            }
            tapCount++;
            consecutiveFailures = 0;
            if (overlay != null) {
                overlay.setTapCount(tapCount);
            }
            notifyState();
            scheduleNext();
        }

        @Override
        public void onCancelled(GestureDescription gestureDescription) {
            onTapFailed();
        }
    };

    private void performTap() {
        if (!running) {
            return;
        }
        if (repeatLimit > 0 && tapCount >= repeatLimit) {
            stopTapping();
            return;
        }

        int[] screen = ScreenUtil.getScreenSize(this);
        float x = targetX;
        float y = targetY;
        if (jitter) {
            int r = TapSettings.JITTER_RADIUS_PX;
            x += random.nextInt(2 * r + 1) - r;
            y += random.nextInt(2 * r + 1) - r;
        }
        x = Math.max(0f, Math.min(x, screen[0] - 1f));
        y = Math.max(0f, Math.min(y, screen[1] - 1f));

        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, holdMs);
        GestureDescription gesture = new GestureDescription.Builder().addStroke(stroke).build();

        lastDispatchAt = SystemClock.uptimeMillis();
        boolean accepted = dispatchGesture(gesture, gestureCallback, handler);
        if (!accepted) {
            onTapFailed();
        }
    }

    private void scheduleNext() {
        if (!running) {
            return;
        }
        long elapsed = SystemClock.uptimeMillis() - lastDispatchAt;
        long delay = Math.max(0L, intervalMs - elapsed);
        handler.postDelayed(tapRunnable, delay);
    }

    private void onTapFailed() {
        if (!running) {
            return;
        }
        consecutiveFailures++;
        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
            stopTapping();
            return;
        }
        handler.postDelayed(tapRunnable, Math.max(intervalMs, 200));
    }

    private static void notifyState() {
        StateListener listener = stateListener;
        if (listener != null) {
            listener.onServiceStateChanged();
        }
    }
}
