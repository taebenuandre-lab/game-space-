package com.zack.gamespace;

import android.content.Context;
import android.graphics.PixelFormat;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

/**
 * Mengelola overlay melayang: crosshair target tap dan panel kontrol kecil.
 * Memakai TYPE_ACCESSIBILITY_OVERLAY sehingga tidak membutuhkan izin
 * "tampil di atas aplikasi lain" - hanya bisa dibuat oleh AccessibilityService.
 */
public class OverlayController {

    public interface Listener {
        void onToggleRequested();

        void onCloseRequested();
    }

    private static final int BASE_FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;

    private final Context context;
    private final WindowManager windowManager;
    private final TapSettings settings;
    private final Listener listener;

    private View targetView;
    private View panelView;
    private WindowManager.LayoutParams targetParams;
    private WindowManager.LayoutParams panelParams;
    private TextView countText;
    private TextView toggleButton;

    private boolean running;
    private long tapCount;

    public OverlayController(Context serviceContext, TapSettings settings, Listener listener) {
        this.context = serviceContext;
        this.windowManager = (WindowManager) serviceContext.getSystemService(Context.WINDOW_SERVICE);
        this.settings = settings;
        this.listener = listener;
    }

    public boolean isShowing() {
        return targetView != null && panelView != null;
    }

    /** @return true bila overlay berhasil ditampilkan (atau sudah tampil). */
    public boolean show() {
        if (isShowing()) {
            return true;
        }
        try {
            Context themed = new ContextThemeWrapper(context, android.R.style.Theme_DeviceDefault);
            LayoutInflater inflater = LayoutInflater.from(themed);
            int[] screen = ScreenUtil.getScreenSize(context);
            int targetSize = context.getResources().getDimensionPixelSize(R.dimen.overlay_target_size);

            // Crosshair target
            targetView = inflater.inflate(R.layout.overlay_target, null);
            targetParams = new WindowManager.LayoutParams(
                    targetSize, targetSize,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    BASE_FLAGS,
                    PixelFormat.TRANSLUCENT);
            targetParams.gravity = Gravity.TOP | Gravity.START;
            int centerX = settings.hasTarget() ? settings.getTargetX() : screen[0] / 2;
            int centerY = settings.hasTarget() ? settings.getTargetY() : screen[1] / 2;
            targetParams.x = clamp(centerX - targetSize / 2, 0, Math.max(0, screen[0] - targetSize));
            targetParams.y = clamp(centerY - targetSize / 2, 0, Math.max(0, screen[1] - targetSize));
            targetView.setOnTouchListener(targetDragListener);

            // Panel kontrol
            panelView = inflater.inflate(R.layout.overlay_panel, null);
            countText = panelView.findViewById(R.id.tv_overlay_count);
            toggleButton = panelView.findViewById(R.id.btn_overlay_toggle);
            View closeButton = panelView.findViewById(R.id.btn_overlay_close);
            toggleButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.onToggleRequested();
                }
            });
            closeButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.onCloseRequested();
                }
            });
            panelView.setOnTouchListener(panelDragListener);

            panelParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            panelParams.gravity = Gravity.TOP | Gravity.START;
            panelParams.x = dp(12);
            panelParams.y = dp(96);

            windowManager.addView(targetView, targetParams);
            windowManager.addView(panelView, panelParams);
            refreshState();
            return true;
        } catch (RuntimeException e) {
            hide();
            return false;
        }
    }

    public void hide() {
        removeQuietly(panelView);
        removeQuietly(targetView);
        panelView = null;
        targetView = null;
        panelParams = null;
        targetParams = null;
        countText = null;
        toggleButton = null;
    }

    public void setRunning(boolean running) {
        this.running = running;
        if (running) {
            tapCount = 0;
        }
        refreshState();
    }

    public void setTapCount(long count) {
        this.tapCount = count;
        refreshState();
    }

    /** Titik tengah crosshair dalam koordinat layar, atau titik tersimpan bila overlay tidak tampil. */
    public int[] getTargetCenter() {
        if (targetView != null && targetParams != null) {
            int w = targetView.getWidth() > 0 ? targetView.getWidth() : targetParams.width;
            int h = targetView.getHeight() > 0 ? targetView.getHeight() : targetParams.height;
            int[] location = new int[2];
            targetView.getLocationOnScreen(location);
            return new int[]{location[0] + w / 2, location[1] + h / 2};
        }
        int[] screen = ScreenUtil.getScreenSize(context);
        if (settings.hasTarget()) {
            return new int[]{settings.getTargetX(), settings.getTargetY()};
        }
        return new int[]{screen[0] / 2, screen[1] / 2};
    }

    private void refreshState() {
        if (!isShowing()) {
            return;
        }
        countText.setText(context.getString(R.string.overlay_count, tapCount));
        toggleButton.setText(running ? R.string.overlay_stop : R.string.overlay_start);
        toggleButton.setBackgroundResource(running ? R.drawable.bg_btn_alert : R.drawable.bg_btn_primary);

        // Saat berjalan crosshair tidak boleh menangkap sentuhan agar tap tembus ke game.
        if (running) {
            targetParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            targetView.setAlpha(0.55f);
        } else {
            targetParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            targetView.setAlpha(1f);
        }
        try {
            windowManager.updateViewLayout(targetView, targetParams);
        } catch (RuntimeException ignored) {
            // View mungkin sudah dilepas oleh sistem.
        }
    }

    private void persistTarget() {
        if (targetParams == null) {
            return;
        }
        settings.setTarget(
                targetParams.x + targetParams.width / 2,
                targetParams.y + targetParams.height / 2);
    }

    private final View.OnTouchListener targetDragListener = new View.OnTouchListener() {
        private float downRawX;
        private float downRawY;
        private int downX;
        private int downY;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (targetParams == null) {
                return false;
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downRawX = event.getRawX();
                    downRawY = event.getRawY();
                    downX = targetParams.x;
                    downY = targetParams.y;
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    int[] screen = ScreenUtil.getScreenSize(context);
                    targetParams.x = clamp(downX + Math.round(event.getRawX() - downRawX),
                            0, Math.max(0, screen[0] - targetParams.width));
                    targetParams.y = clamp(downY + Math.round(event.getRawY() - downRawY),
                            0, Math.max(0, screen[1] - targetParams.height));
                    try {
                        windowManager.updateViewLayout(targetView, targetParams);
                    } catch (RuntimeException ignored) {
                        // abaikan bila window sudah hilang
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    persistTarget();
                    return true;
                default:
                    return false;
            }
        }
    };

    private final View.OnTouchListener panelDragListener = new View.OnTouchListener() {
        private float downRawX;
        private float downRawY;
        private int downX;
        private int downY;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (panelParams == null) {
                return false;
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downRawX = event.getRawX();
                    downRawY = event.getRawY();
                    downX = panelParams.x;
                    downY = panelParams.y;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    panelParams.x = Math.max(0, downX + Math.round(event.getRawX() - downRawX));
                    panelParams.y = Math.max(0, downY + Math.round(event.getRawY() - downRawY));
                    try {
                        windowManager.updateViewLayout(panelView, panelParams);
                    } catch (RuntimeException ignored) {
                        // abaikan bila window sudah hilang
                    }
                    return true;
                default:
                    return false;
            }
        }
    };

    private void removeQuietly(View view) {
        if (view == null) {
            return;
        }
        try {
            windowManager.removeViewImmediate(view);
        } catch (RuntimeException ignored) {
            // View belum/ sudah tidak terpasang.
        }
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
