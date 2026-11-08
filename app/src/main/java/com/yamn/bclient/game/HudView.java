package com.yamn.bclient.game;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import com.yamn.bclient.core.K;
import com.yamn.bclient.core.Prefs;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Draws the HUD modules. In edit mode modules can be dragged and pinched to resize. */
@SuppressLint("ViewConstructor")
final class HudView extends View {
    private final Prefs p;
    boolean edit;

    // live stats (set by Overlay)
    int fps = -1;
    int battery = -1;
    boolean charging;
    long ramUsed, ramTotal;
    long sessionStart = SystemClock.uptimeMillis();

    private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint op = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF[] rects = new RectF[K.MODS.length];
    private final SimpleDateFormat clock = new SimpleDateFormat("HH:mm", Locale.US);
    private final ScaleGestureDetector sgd;
    private final float d;

    private int drag = -1;
    private float offX, offY;

    HudView(Context c, Prefs p) {
        super(c);
        this.p = p;
        d = getResources().getDisplayMetrics().density;
        op.setStyle(Paint.Style.STROKE);
        op.setStrokeWidth(1.5f * d);
        op.setColor(0xFF4ADE80);
        op.setPathEffect(new DashPathEffect(new float[]{6 * d, 4 * d}, 0));
        cp.setStrokeCap(Paint.Cap.ROUND);
        sgd = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector g) {
                if (drag < 0) return false;
                String k = K.mod(K.MODS[drag], "size");
                int sz = Math.round(p.getInt(k, 100) * g.getScaleFactor());
                p.set(k, Math.max(50, Math.min(300, sz)));
                invalidate();
                return true;
            }
        });
    }

    boolean anyRainbow() {
        for (int i = 0; i < K.MODS.length; i++) {
            if (p.getBool(K.mod(K.MODS[i], "on"), K.MOD_ON[i]) && p.getInt(K.mod(K.MODS[i], "color"), K.MOD_COLOR[i]) == 0)
                return true;
        }
        return false;
    }

    private int colorFor(int i) {
        int c = p.getInt(K.mod(K.MODS[i], "color"), K.MOD_COLOR[i]);
        if (c != 0) return c;
        float hue = ((SystemClock.uptimeMillis() / 12f) + i * 45f) % 360f;
        return Color.HSVToColor(new float[]{hue, 0.75f, 1f});
    }

    private String textFor(String id) {
        switch (id) {
            case "fps":
                return "FPS " + (fps < 0 ? "--" : fps);
            case "clock":
                return clock.format(new Date());
            case "battery":
                return (charging ? "⚡ " : "🔋 ") + (battery < 0 ? "--" : battery) + "%";
            case "ram":
                return String.format(Locale.US, "RAM %.1f/%.1fG", ramUsed / 1073741824f, ramTotal / 1073741824f);
            case "session": {
                long s = (SystemClock.uptimeMillis() - sessionStart) / 1000;
                long h = s / 3600, m = (s / 60) % 60, sec = s % 60;
                return "⏱ " + (h > 0 ? String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
                        : String.format(Locale.US, "%02d:%02d", m, sec));
            }
            case "text":
                return p.getStr(K.HUD_TEXT, "Bedrock Client");
        }
        return "";
    }

    @Override
    protected void onDraw(Canvas c) {
        if (!p.getBool(K.HUD_ON, true) && !edit) return;
        int W = getWidth(), H = getHeight();
        int font = p.getInt(K.HUD_FONT, 1);
        tp.setTypeface(font == 2 ? Typeface.MONOSPACE : font == 1 ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        boolean shadow = p.getBool(K.HUD_SHADOW, true);
        float radius = p.getInt(K.HUD_RADIUS, 8) * d;

        for (int i = 0; i < K.MODS.length; i++) {
            String id = K.MODS[i];
            rects[i] = null;
            if (!p.getBool(K.mod(id, "on"), K.MOD_ON[i])) continue;
            float scale = p.getInt(K.mod(id, "size"), 100) / 100f;
            int color = colorFor(i);
            if (i == K.CROSSHAIR) {
                drawCrosshair(c, W / 2f, H / 2f, scale, color, p.getInt(K.mod(id, "style"), 0), shadow);
                continue;
            }
            String s = textFor(id);
            if (s.isEmpty()) continue;
            tp.setTextSize(13f * d * scale);
            if (shadow) tp.setShadowLayer(2f * d, 0, d, 0xCC000000);
            else tp.clearShadowLayer();
            Paint.FontMetrics fm = tp.getFontMetrics();
            float pad = 6f * d * scale;
            float tw = tp.measureText(s), th = fm.descent - fm.ascent;
            float bw = tw + pad * 2, bh = th + pad * 1.4f;
            float x = p.getFloat(K.mod(id, "x"), K.defX(i)) * W;
            float y = p.getFloat(K.mod(id, "y"), K.defY(i)) * H;
            x = Math.max(0, Math.min(W - bw, x));
            y = Math.max(0, Math.min(H - bh, y));
            RectF r = new RectF(x, y, x + bw, y + bh);
            int bgA = p.getInt(K.mod(id, "bg"), 110);
            if (bgA > 0) {
                bp.setColor(Color.argb(bgA, 0, 0, 0));
                c.drawRoundRect(r, radius, radius, bp);
            }
            tp.setColor(color);
            c.drawText(s, x + pad, y + pad * 0.7f - fm.ascent, tp);
            if (edit) c.drawRoundRect(r, radius, radius, op);
            rects[i] = r;
        }

        if (edit) {
            tp.clearShadowLayer();
            tp.setTextSize(14f * d);
            tp.setColor(Color.WHITE);
            tp.setTypeface(Typeface.DEFAULT_BOLD);
            String hint = "اسحب العناصر لتحريكها • قرّب إصبعين لتغيير الحجم";
            float tw = tp.measureText(hint);
            float x = (W - tw) / 2f, y = H * 0.30f;
            bp.setColor(0xB3000000);
            c.drawRoundRect(x - 12 * d, y - 22 * d, x + tw + 12 * d, y + 10 * d, 10 * d, 10 * d, bp);
            c.drawText(hint, x, y, tp);
        }
    }

    private void drawCrosshair(Canvas c, float cx, float cy, float s, int color, int style, boolean shadow) {
        float len = 9f * d * s, th = 2f * d * s;
        for (int pass = shadow ? 0 : 1; pass < 2; pass++) {
            boolean sh = pass == 0;
            cp.setColor(sh ? 0x99000000 : color);
            float o = sh ? 0.8f * d : 0;
            cp.setStrokeWidth(th + (sh ? 1.5f * d : 0));
            switch (style) {
                case 1: // dot
                    cp.setStyle(Paint.Style.FILL);
                    c.drawCircle(cx + o, cy + o, th * 1.4f + (sh ? d * 0.8f : 0), cp);
                    break;
                case 2: // circle
                    cp.setStyle(Paint.Style.STROKE);
                    c.drawCircle(cx + o, cy + o, len * 0.8f, cp);
                    cp.setStyle(Paint.Style.FILL);
                    c.drawCircle(cx + o, cy + o, th * 0.9f, cp);
                    break;
                case 3: // x
                    cp.setStyle(Paint.Style.STROKE);
                    float k = len * 0.75f;
                    c.drawLine(cx - k + o, cy - k + o, cx + k + o, cy + k + o, cp);
                    c.drawLine(cx - k + o, cy + k + o, cx + k + o, cy - k + o, cp);
                    break;
                default: // plus
                    cp.setStyle(Paint.Style.STROKE);
                    c.drawLine(cx - len + o, cy + o, cx + len + o, cy + o, cp);
                    c.drawLine(cx + o, cy - len + o, cx + o, cy + len + o, cp);
            }
        }
    }

    private int hit(float x, float y) {
        float m = 12 * d;
        for (int i = rects.length - 1; i >= 0; i--) {
            RectF r = rects[i];
            if (r != null && x >= r.left - m && x <= r.right + m && y >= r.top - m && y <= r.bottom + m) return i;
        }
        return -1;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!edit) return false;
        sgd.onTouchEvent(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                drag = hit(e.getX(), e.getY());
                if (drag >= 0) {
                    offX = e.getX() - rects[drag].left;
                    offY = e.getY() - rects[drag].top;
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (drag >= 0 && !sgd.isInProgress() && e.getPointerCount() == 1) {
                    float nx = (e.getX() - offX) / getWidth();
                    float ny = (e.getY() - offY) / getHeight();
                    p.set(K.mod(K.MODS[drag], "x"), Math.max(0f, Math.min(1f, nx)));
                    p.set(K.mod(K.MODS[drag], "y"), Math.max(0f, Math.min(1f, ny)));
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (drag >= 0) p.save();
                drag = -1;
                return true;
        }
        return true;
    }
}
