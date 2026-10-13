package com.zack.gamespace;

import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.WindowManager;

/**
 * Utilitas ukuran layar fisik (koordinat yang sama dengan gesture accessibility).
 */
final class ScreenUtil {

    private ScreenUtil() {
    }

    /** @return {lebar, tinggi} layar dalam piksel pada rotasi saat ini. */
    @SuppressWarnings("deprecation")
    static int[] getScreenSize(Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Rect bounds = wm.getMaximumWindowMetrics().getBounds();
            return new int[]{bounds.width(), bounds.height()};
        }
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        return new int[]{dm.widthPixels, dm.heightPixels};
    }
}
