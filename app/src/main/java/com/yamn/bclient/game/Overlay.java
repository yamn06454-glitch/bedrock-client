package com.yamn.bclient.game;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.yamn.bclient.core.K;
import com.yamn.bclient.core.Native;
import com.yamn.bclient.core.Prefs;
import com.yamn.bclient.ui.SettingsUi;
import com.yamn.bclient.ui.Ui;

/**
 * Everything drawn on top of the game: HUD, floating buttons, client menu.
 * Uses child panel windows of the game window, so no overlay permission is needed
 * and touches outside our buttons go straight to Minecraft.
 */
final class Overlay {
    final Activity act;
    final WindowManager wm;
    final Prefs p;
    final Context ui;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final long start = SystemClock.uptimeMillis();

    IBinder token;
    boolean editing;
    private boolean attached, zoomed, restartExpired;

    private HudView hud;
    private WindowManager.LayoutParams hudLp;
    private boolean hudShown;
    private FloatButton menuB, zoomB, backB, shotB, restartB, doneB;
    private View menu;

    private long lastFrames = -1, lastFrameT, lastBat, lastRam;
    private int lastSize = -1, lastAlpha = -1;
    private String lastPos = "";

    Overlay(Activity a) {
        act = a;
        wm = a.getWindowManager();
        p = Prefs.get(a);
        ui = new ContextThemeWrapper(a, android.R.style.Theme_DeviceDefault);
    }

    // ------------------------------------------------------------ lifecycle

    void attach() {
        if (attached) return;
        token = act.getWindow().getDecorView().getWindowToken();
        if (token == null) {
            h.postDelayed(this::attach, 250);
            return;
        }
        attached = true;

        hud = new HudView(ui, p);
        hud.sessionStart = start;
        hudLp = params(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, false);
        try {
            wm.addView(hud, hudLp);
            hudShown = true;
        } catch (Throwable ignored) {
        }

        menuB = new FloatButton(ui, this, "menu", "☰", false, 0.012f, 0.40f, new FloatButton.Listener() {
            @Override public void onTap() { openMenu(); }
        });
        zoomB = new FloatButton(ui, this, "zoom", "🔍", false, 0.90f, 0.28f, new FloatButton.Listener() {
            @Override public void onPress() { if (holdMode()) setZoom(true); }
            @Override public void onRelease() { if (holdMode()) setZoom(false); }
            @Override public void onTap() { if (!holdMode()) setZoom(!zoomed); }
        });
        backB = new FloatButton(ui, this, "back", "←", false, 0.012f, 0.54f, new FloatButton.Listener() {
            @Override public void onTap() { Actions.back(act); }
        });
        shotB = new FloatButton(ui, this, "shot", "📷", false, 0.012f, 0.68f, new FloatButton.Listener() {
            @Override public void onTap() { Actions.screenshot(act); }
        });
        restartB = new FloatButton(ui, this, "restart", "⟳  إعادة تشغيل اللعبة", true, -1f, 0.04f, new FloatButton.Listener() {
            @Override public void onTap() { Actions.restart(act); }
        });
        restartB.draggable = false;

        apply();
        h.post(tick);
    }

    void onResume() {
        p.reload();
        if (attached) apply();
    }

    void detach() {
        h.removeCallbacksAndMessages(null);
        closeMenu();
        for (FloatButton b : new FloatButton[]{menuB, zoomB, backB, shotB, restartB, doneB}) if (b != null) b.hide();
        if (hudShown) {
            try {
                wm.removeViewImmediate(hud);
            } catch (Throwable ignored) {
            }
            hudShown = false;
        }
        attached = false;
    }

    // ------------------------------------------------------------ settings

    /** Re-applies every setting (called whenever something changes). */
    void apply() {
        if (!attached) return;
        Native.mode(p.getInt(K.ZOOM_MODE, 0));
        Native.smooth(p.getBool(K.ZOOM_SMOOTH, true));
        boolean zOn = p.getBool(K.ZOOM_ON, true);
        if (zoomed) {
            if (!zOn) setZoom(false);
            else Native.zoom(zoomLevel());
        }

        int size = p.getInt(K.BTN_SIZE, 100), alpha = p.getInt(K.BTN_ALPHA, 220);
        String pos = p.dump(K.BTN_POS);
        boolean restyle = size != lastSize || alpha != lastAlpha || !pos.equals(lastPos);
        lastSize = size;
        lastAlpha = alpha;
        lastPos = pos;

        boolean added = vis(menuB, true, restyle);
        added |= vis(zoomB, zOn, restyle);
        added |= vis(backB, p.getBool(K.BTN_BACK, true), restyle);
        added |= vis(shotB, p.getBool(K.BTN_SHOT, false), restyle);
        added |= vis(restartB, p.getBool(K.RESTART_BTN, true) && !restartExpired, restyle);
        if (added && menu != null) bringMenuToFront(); // keep menu above re-added buttons
        if (hud != null) hud.invalidate();
    }

    /** @return true if a window was (re)added */
    private boolean vis(FloatButton b, boolean on, boolean restyle) {
        if (!on) {
            b.hide();
            return false;
        }
        if (b.shown && !restyle) return false;
        if (b.shown) b.refresh();
        else b.show();
        return b.shown;
    }

    private boolean holdMode() { return p.getInt(K.ZOOM_HOLD, 0) == 0; }

    private float zoomLevel() { return p.getInt(K.ZOOM_LEVEL, 40) / 10f; }

    private void setZoom(boolean on) {
        zoomed = on;
        Native.zoom(on ? zoomLevel() : 1f);
        zoomB.active = on;
        zoomB.invalidate();
    }

    // ------------------------------------------------------------ ticker

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!attached) return;
            long now = SystemClock.uptimeMillis();
            long f = Native.frameCount();
            if (lastFrames < 0) {
                lastFrames = f;
                lastFrameT = now;
            } else if (now - lastFrameT >= 1000) {
                hud.fps = Native.isLoaded() ? Math.round((f - lastFrames) * 1000f / (now - lastFrameT)) : -1;
                lastFrames = f;
                lastFrameT = now;
            }
            if (now - lastBat > 5000) {
                lastBat = now;
                try {
                    BatteryManager bm = (BatteryManager) act.getSystemService(Context.BATTERY_SERVICE);
                    hud.battery = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                    hud.charging = bm.isCharging();
                } catch (Throwable ignored) {
                }
            }
            if (now - lastRam > 3000) {
                lastRam = now;
                try {
                    ActivityManager am = (ActivityManager) act.getSystemService(Context.ACTIVITY_SERVICE);
                    ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                    am.getMemoryInfo(mi);
                    hud.ramTotal = mi.totalMem;
                    hud.ramUsed = mi.totalMem - mi.availMem;
                } catch (Throwable ignored) {
                }
            }
            if (!restartExpired && now - start > p.getInt(K.RESTART_SECS, 120) * 1000L) {
                restartExpired = true;
                restartB.hide();
            }
            hud.invalidate();
            h.postDelayed(this, hud.anyRainbow() ? 50 : 250);
        }
    };

    // ------------------------------------------------------------ windows

    WindowManager.LayoutParams params(int w, int hgt, boolean touchable) {
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        if (!touchable) flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(w, hgt,
                WindowManager.LayoutParams.TYPE_APPLICATION_PANEL, flags, PixelFormat.TRANSLUCENT);
        lp.token = token;
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        return lp;
    }

    int[] screen() {
        View d = act.getWindow().getDecorView();
        int w = d.getWidth(), hh = d.getHeight();
        if (w <= 0 || hh <= 0) {
            DisplayMetrics dm = new DisplayMetrics();
            act.getWindowManager().getDefaultDisplay().getRealMetrics(dm);
            w = dm.widthPixels;
            hh = dm.heightPixels;
        }
        return new int[]{w, hh};
    }

    // ------------------------------------------------------------ menu

    void openMenu() {
        if (menu != null || token == null) return;
        if (zoomed) setZoom(false);
        p.reload();
        int[] s = screen();

        FrameLayout dim = new FrameLayout(ui);
        dim.setBackgroundColor(0x99000000);
        dim.setOnClickListener(v -> closeMenu());

        LinearLayout card = Ui.vbox(ui);
        card.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        card.setBackground(Ui.round(ui, Ui.BG, 18));
        card.setClickable(true);
        int pad = Ui.dp(ui, 14);
        card.setPadding(pad, pad, pad, pad);

        LinearLayout head = Ui.hbox(ui);
        head.addView(Ui.text(ui, "Bedrock Client", 20, Ui.TEXT, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView close = Ui.button(ui, "✕", Ui.CARD2, Ui.TEXT, v -> closeMenu());
        head.addView(close, new LinearLayout.LayoutParams(Ui.dp(ui, 44), Ui.dp(ui, 44)));
        card.addView(head);

        ScrollView sv = new ScrollView(ui);
        LinearLayout content = Ui.vbox(ui);
        sv.addView(content);
        card.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout r1 = Ui.hbox(ui);
        int m = Ui.dp(ui, 4);
        r1.addView(Ui.button(ui, "✏️ تعديل أماكن الـHUD", Ui.CARD, Ui.TEXT, v -> startEdit()), Ui.weight(1, m));
        r1.addView(Ui.button(ui, "⟳ إعادة تشغيل اللعبة", Ui.CARD, Ui.TEXT, v -> Actions.restart(act)), Ui.weight(1, m));
        content.addView(r1, Ui.lp(-1, -2, Ui.dp(ui, 10)));
        LinearLayout r2 = Ui.hbox(ui);
        r2.addView(Ui.button(ui, "📸 لقطة شاشة", Ui.CARD, Ui.TEXT, v -> {
            closeMenu();
            h.postDelayed(() -> Actions.screenshot(act), 250);
        }), Ui.weight(1, m));
        r2.addView(Ui.button(ui, "← رجوع", Ui.CARD, Ui.TEXT, v -> {
            closeMenu();
            Actions.back(act);
        }), Ui.weight(1, m));
        content.addView(r2, Ui.lp(-1, -2, Ui.dp(ui, 8)));

        content.addView(SettingsUi.build(ui, p, true, this::apply));

        int be = Native.renderBackend();
        String backend = be == 1 ? "OpenGL ES" : be == 2 ? "Vulkan" : "؟";
        String note = be == 2 ? "\n⚠️ اللعبة شغالة على Vulkan — الزوم حالياً يدعم OpenGL ES بس." : "";
        TextView st = Ui.text(ui, "الرسم: " + backend + " • Hooks: " + Native.status() + note, 11, Ui.SUB, false);
        content.addView(st, Ui.lp(-1, -2, Ui.dp(ui, 14)));

        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                Math.min(Math.round(s[0] * 0.64f), Ui.dp(ui, 620)), Math.round(s[1] * 0.92f), Gravity.CENTER);
        dim.addView(card, clp);

        try {
            wm.addView(dim, params(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, true));
            menu = dim;
        } catch (Throwable ignored) {
        }
    }

    private void bringMenuToFront() {
        View m = menu;
        if (m == null) return;
        try {
            WindowManager.LayoutParams lp = (WindowManager.LayoutParams) m.getLayoutParams();
            wm.removeViewImmediate(m);
            wm.addView(m, lp);
        } catch (Throwable ignored) {
        }
    }

    void closeMenu() {
        if (menu == null) return;
        try {
            wm.removeViewImmediate(menu);
        } catch (Throwable ignored) {
        }
        menu = null;
    }

    // ------------------------------------------------------------ HUD edit

    private void startEdit() {
        closeMenu();
        editing = true;
        hud.edit = true;
        hudLp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        try {
            wm.updateViewLayout(hud, hudLp);
        } catch (Throwable ignored) {
        }
        // edit mode: show the main buttons so they can be placed too
        if (doneB == null) {
            doneB = new FloatButton(ui, this, "done", "✓  حفظ الأماكن", true, -1f, 0.14f, new FloatButton.Listener() {
                @Override public void onTap() { endEdit(); }
            });
            doneB.draggable = false;
        }
        doneB.show();
        hud.invalidate();
    }

    private void endEdit() {
        editing = false;
        hud.edit = false;
        hudLp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        try {
            wm.updateViewLayout(hud, hudLp);
        } catch (Throwable ignored) {
        }
        if (doneB != null) doneB.hide();
        p.save();
        hud.invalidate();
        Actions.toast(act, "✓ انحفظت الأماكن");
    }
}
