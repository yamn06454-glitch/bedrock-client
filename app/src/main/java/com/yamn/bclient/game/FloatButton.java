package com.yamn.bclient.game;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;

import com.yamn.bclient.core.K;
import com.yamn.bclient.ui.Ui;

/** A small draggable button living in its own panel window above the game. */
@SuppressLint("ViewConstructor")
final class FloatButton extends View {
    interface Listener {
        default void onTap() {}
        default void onPress() {}
        default void onRelease() {}
    }

    private final Overlay ov;
    final String id;
    String label;
    private final boolean pill;
    private final float defX, defY;   // fractions of screen; defX < 0 => centered
    private final Listener listener;
    boolean active;
    boolean draggable = true;

    WindowManager.LayoutParams lp;
    boolean shown;

    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int slop;
    private float dX, dY;
    private int sX, sY;
    private boolean dragging, pressed;

    FloatButton(Context c, Overlay ov, String id, String label, boolean pill, float defX, float defY, Listener l) {
        super(c);
        this.ov = ov;
        this.id = id;
        this.label = label;
        this.pill = pill;
        this.defX = defX;
        this.defY = defY;
        this.listener = l;
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(Ui.dp(c, 1.5f));
        tp.setColor(Color.WHITE);
        tp.setTextAlign(Paint.Align.CENTER);
        tp.setTypeface(Typeface.DEFAULT_BOLD);
    }

    private int base() {
        return Math.round(Ui.dp(getContext(), 46) * ov.p.getInt(K.BTN_SIZE, 100) / 100f);
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int b = base();
        int h = pill ? Math.round(b * 0.85f) : b;
        tp.setTextSize(pill ? h * 0.36f : h * 0.44f);
        int w = pill ? Math.round(tp.measureText(label) + h) : h;
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), r = h / 2f, in = ring.getStrokeWidth();
        bg.setColor(active ? 0xE64ADE80 : Color.argb(185, 18, 20, 26));
        c.drawRoundRect(0, 0, w, h, r, r, bg);
        ring.setColor(active ? Color.WHITE : 0xB34ADE80);
        c.drawRoundRect(in, in, w - in, h - in, r, r, ring);
        tp.setColor(active ? 0xFF0B0D10 : Color.WHITE);
        c.drawText(label, w / 2f, h / 2f - (tp.descent() + tp.ascent()) / 2f, tp);
    }

    private boolean canDrag() {
        return draggable && (ov.editing || !ov.p.getBool(K.BTN_LOCK, false));
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dX = e.getRawX();
                dY = e.getRawY();
                sX = lp.x;
                sY = lp.y;
                dragging = false;
                pressed = true;
                listener.onPress();
                return true;
            case MotionEvent.ACTION_MOVE: {
                float mx = e.getRawX() - dX, my = e.getRawY() - dY;
                if (!dragging && canDrag() && (Math.abs(mx) > slop || Math.abs(my) > slop)) {
                    dragging = true;
                    if (pressed) {
                        pressed = false;
                        listener.onRelease();
                    }
                }
                if (dragging) {
                    int[] s = ov.screen();
                    lp.x = clamp(sX + Math.round(mx), 0, s[0] - getWidth());
                    lp.y = clamp(sY + Math.round(my), 0, s[1] - getHeight());
                    try {
                        ov.wm.updateViewLayout(this, lp);
                    } catch (Throwable ignored) {
                    }
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
                if (dragging) savePos();
                else if (pressed) {
                    pressed = false;
                    listener.onRelease();
                    listener.onTap();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (pressed) {
                    pressed = false;
                    listener.onRelease();
                }
                if (dragging) savePos();
                return true;
        }
        return false;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(Math.max(lo, hi), v));
    }

    void show() {
        if (shown || ov.token == null) return;
        int[] s = ov.screen();
        measure(0, 0);
        lp = ov.params(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, true);
        float fx = ov.p.getFloat(K.BTN_POS + id + ".x", defX);
        float fy = ov.p.getFloat(K.BTN_POS + id + ".y", defY);
        lp.x = fx < 0 ? (s[0] - getMeasuredWidth()) / 2 : clamp(Math.round(fx * s[0]), 0, s[0] - getMeasuredWidth());
        lp.y = clamp(Math.round(fy * s[1]), 0, s[1] - getMeasuredHeight());
        setAlpha(ov.p.getInt(K.BTN_ALPHA, 220) / 255f);
        try {
            ov.wm.addView(this, lp);
            shown = true;
        } catch (Throwable ignored) {
        }
    }

    void hide() {
        if (!shown) return;
        try {
            ov.wm.removeViewImmediate(this);
        } catch (Throwable ignored) {
        }
        shown = false;
    }

    /** Re-applies size/alpha (and default position if it was reset). */
    void refresh() {
        if (!shown) return;
        hide();
        show();
    }

    private void savePos() {
        int[] s = ov.screen();
        ov.p.set(K.BTN_POS + id + ".x", lp.x / (float) s[0]);
        ov.p.set(K.BTN_POS + id + ".y", lp.y / (float) s[1]);
        ov.p.save();
    }
}
