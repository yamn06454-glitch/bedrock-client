package com.yamn.bclient.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Code-only UI helpers (no app resources, so they also work inside the game process). */
public final class Ui {
    public static final int BG = 0xFF0F1115;
    public static final int CARD = 0xFF1A1D24;
    public static final int CARD2 = 0xFF232733;
    public static final int ACCENT = 0xFF4ADE80;
    public static final int TEXT = 0xFFE8EAED;
    public static final int SUB = 0xFF9AA0A6;
    public static final int DANGER = 0xFFEF4444;

    private Ui() {}

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    public static GradientDrawable round(Context c, int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    public static LinearLayout vbox(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout hbox(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        return t;
    }

    public static TextView button(Context c, String s, int bg, int fg, View.OnClickListener l) {
        TextView b = text(c, s, 15, fg, true);
        b.setGravity(Gravity.CENTER);
        b.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        int p = dp(c, 12);
        b.setPadding(p, p, p, p);
        b.setBackground(round(c, bg, 12));
        b.setClickable(true);
        b.setOnClickListener(l);
        return b;
    }

    public static LinearLayout.LayoutParams lp(int w, int h, int topMarginPx) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        lp.topMargin = topMarginPx;
        return lp;
    }

    public static LinearLayout.LayoutParams weight(float w, int sideMarginPx) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, w);
        lp.leftMargin = sideMarginPx;
        lp.rightMargin = sideMarginPx;
        return lp;
    }
}
