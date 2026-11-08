package org.levimc.launcher.core.mods.inbuilt.overlay;

import android.content.Context;
import android.graphics.Color;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;

import org.levimc.launcher.core.mods.inbuilt.manager.InbuiltModManager;

/** Bedrock Client: shared colour styling for text HUD elements (FPS, CPS, Info). */
public final class HudStyle {
    private HudStyle() {}

    public static void apply(Context context, String modId, View background, TextView text) {
        InbuiltModManager manager = InbuiltModManager.getInstance(context);
        int opacity = manager.getOverlayOpacity(modId);
        int bg = manager.getHudBackgroundColor(modId);
        int alpha = Math.round(Color.alpha(bg) * (opacity / 100f));
        if (background != null) {
            background.setBackgroundColor(Color.argb(alpha, Color.red(bg), Color.green(bg), Color.blue(bg)));
        }
        if (text != null) {
            text.setTextColor(manager.getHudTextColor(modId));
            if (manager.isHudTextShadow(modId)) {
                text.setShadowLayer(2f, 1.5f, 1.5f, 0xFF000000);
            } else {
                text.setShadowLayer(0f, 0f, 0f, 0);
            }
        }
    }

    /** Call on every display refresh; animates the text when rainbow mode is on. */
    public static void tick(Context context, String modId, TextView text) {
        if (text == null) return;
        InbuiltModManager manager = InbuiltModManager.getInstance(context);
        if (!manager.isHudRainbow(modId)) return;
        float hue = (SystemClock.uptimeMillis() / 12L) % 360L;
        text.setTextColor(Color.HSVToColor(new float[]{hue, 0.75f, 1f}));
    }
}
