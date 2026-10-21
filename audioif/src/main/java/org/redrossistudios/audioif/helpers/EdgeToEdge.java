package org.redrossistudios.audioif.helpers;

import android.app.Activity;
import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Android 15+ draws apps edge to edge, under the status and navigation bars, and
 * stops resizing the layout for the keyboard. Padding the content view by those
 * insets keeps buttons and the command field visible.
 */
public final class EdgeToEdge {

    private EdgeToEdge() {
    }

    public static void applySystemBarPadding(Activity activity) {
        View content = activity.findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(content, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout()
                    | WindowInsetsCompat.Type.ime());
            // AppCompat lays the content out under its action bar and reports the
            // action bar height only through the legacy system window inset.
            @SuppressWarnings("deprecation")
            int top = Math.max(bars.top, insets.getSystemWindowInsetTop());
            view.setPadding(bars.left, top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(content);
    }
}
