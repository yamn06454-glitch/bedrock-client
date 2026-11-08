package com.yamn.bclient.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.SweepGradient;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import com.yamn.bclient.core.K;
import com.yamn.bclient.core.Prefs;

import java.util.function.IntFunction;

/** Builds the settings screen. Same UI is used in the launcher and in the in-game menu. */
public final class SettingsUi {
    private final Context c;
    private final Prefs p;
    private final boolean inGame;
    private final Runnable onChange;

    private SettingsUi(Context c, Prefs p, boolean inGame, Runnable onChange) {
        this.c = c;
        this.p = p;
        this.inGame = inGame;
        this.onChange = onChange == null ? () -> {} : onChange;
    }

    public static View build(Context c, Prefs p, boolean inGame, Runnable onChange) {
        return new SettingsUi(c, p, inGame, onChange).root();
    }

    private View root() {
        LinearLayout root = Ui.vbox(c);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        // ---------- Zoom ----------
        LinearLayout s = section(root, "🔍 الزوم");
        sw(s, "تفعيل الزوم", K.ZOOM_ON, true);
        choice(s, "نوع الزوم", K.ZOOM_MODE, new String[]{"حقيقي (FOV)", "رقمي"}, 0);
        hint(s, "الحقيقي يقرّب العالم بدون ما يكبّر الـHUD. إذا ما اشتغل على جهازك جرّب الرقمي.");
        seek(s, "قوة الزوم", K.ZOOM_LEVEL, 15, 100, 40, v -> String.format(java.util.Locale.US, "%.1fx", v / 10f));
        choice(s, "طريقة الزر", K.ZOOM_HOLD, new String[]{"اضغط مطوّل", "تشغيل / إيقاف"}, 0);
        sw(s, "زوم ناعم (حركة سلسة)", K.ZOOM_SMOOTH, true);

        // ---------- Buttons ----------
        s = section(root, "🔘 الأزرار العائمة");
        sw(s, "زر الرجوع (Back)", K.BTN_BACK, true);
        sw(s, "زر لقطة الشاشة", K.BTN_SHOT, false);
        seek(s, "حجم الأزرار", K.BTN_SIZE, 60, 170, 100, v -> v + "%");
        seek(s, "شفافية الأزرار", K.BTN_ALPHA, 40, 255, 220, v -> Math.round(v * 100f / 255f) + "%");
        sw(s, "قفل أماكن الأزرار", K.BTN_LOCK, false);
        action(s, "↺ إرجاع أماكن الأزرار الافتراضية", () -> {
            p.removePrefix(K.BTN_POS);
            p.save();
            onChange.run();
        });

        // ---------- HUD ----------
        s = section(root, "🖥️ الـHUD");
        sw(s, "إظهار الـHUD", K.HUD_ON, true);
        choice(s, "الخط", K.HUD_FONT, new String[]{"عادي", "عريض", "Mono"}, 1);
        sw(s, "ظل للنص", K.HUD_SHADOW, true);
        seek(s, "استدارة الخلفية", K.HUD_RADIUS, 0, 20, 8, v -> v + "dp");
        if (!inGame) {
            TextView t = Ui.text(c, "النص المخصص", 14, Ui.TEXT, false);
            s.addView(t, Ui.lp(-1, -2, Ui.dp(c, 10)));
            EditText e = new EditText(c);
            e.setText(p.getStr(K.HUD_TEXT, "Bedrock Client"));
            e.setTextColor(Ui.TEXT);
            e.setHintTextColor(Ui.SUB);
            e.setSingleLine(true);
            e.setInputType(InputType.TYPE_CLASS_TEXT);
            e.setBackground(Ui.round(c, Ui.CARD2, 10));
            int pd = Ui.dp(c, 10);
            e.setPadding(pd, pd, pd, pd);
            e.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence a, int b, int d, int f) {}
                public void onTextChanged(CharSequence a, int b, int d, int f) {}
                public void afterTextChanged(Editable ed) {
                    p.set(K.HUD_TEXT, ed.toString());
                    p.save();
                    onChange.run();
                }
            });
            s.addView(e, Ui.lp(-1, -2, Ui.dp(c, 6)));
        }
        for (int i = 0; i < K.MODS.length; i++) module(s, i);
        hint(s, inGame ? "تقدر تحرّك العناصر من زر \"تعديل أماكن الـHUD\" فوق."
                : "تحريك العناصر يكون داخل اللعبة: افتح القائمة ☰ ← تعديل أماكن الـHUD.");
        action(s, "↺ إرجاع أماكن الـHUD الافتراضية", () -> {
            for (String id : K.MODS) {
                p.removePrefix(K.mod(id, "x"));
                p.removePrefix(K.mod(id, "y"));
            }
            p.save();
            onChange.run();
        });

        // ---------- Game ----------
        s = section(root, "🎮 اللعبة");
        sw(s, "زر إعادة التشغيل في شاشة التحميل", K.RESTART_BTN, true);
        seek(s, "مدة ظهور زر إعادة التشغيل", K.RESTART_SECS, 20, 300, 120, v -> v + " ث");
        sw(s, "أعلى معدل تحديث للشاشة (90/120Hz)", K.HI_REFRESH, true);
        sw(s, "إبقاء الشاشة شغالة", K.KEEP_ON, true);
        if (!inGame) hint(s, "تغييرات معدل التحديث وإبقاء الشاشة تتطبق عند تشغيل اللعبة القادم.");

        return root;
    }

    // ---------------------------------------------------------------- builders

    private LinearLayout section(LinearLayout root, String title) {
        LinearLayout card = Ui.vbox(c);
        card.setBackground(Ui.round(c, Ui.CARD, 16));
        int pd = Ui.dp(c, 14);
        card.setPadding(pd, pd, pd, pd);
        card.addView(Ui.text(c, title, 17, Ui.TEXT, true));
        root.addView(card, Ui.lp(-1, -2, Ui.dp(c, 12)));
        return card;
    }

    private void hint(LinearLayout s, String text) {
        s.addView(Ui.text(c, text, 12, Ui.SUB, false), Ui.lp(-1, -2, Ui.dp(c, 6)));
    }

    private Switch sw(LinearLayout s, String title, String key, boolean def) {
        Switch w = new Switch(c);
        w.setText(title);
        w.setTextColor(Ui.TEXT);
        w.setTextSize(15);
        w.setChecked(p.getBool(key, def));
        tint(w);
        w.setOnCheckedChangeListener((b, on) -> {
            p.set(key, on);
            p.save();
            onChange.run();
        });
        s.addView(w, Ui.lp(-1, Ui.dp(c, 44), Ui.dp(c, 4)));
        return w;
    }

    private void tint(Switch w) {
        int[][] st = {{android.R.attr.state_checked}, {}};
        w.setThumbTintList(new ColorStateList(st, new int[]{Ui.ACCENT, 0xFFBDBDBD}));
        w.setTrackTintList(new ColorStateList(st, new int[]{0x884ADE80, 0x55FFFFFF}));
    }

    private void seek(LinearLayout s, String title, String key, int min, int max, int def, IntFunction<String> fmt) {
        LinearLayout row = Ui.hbox(c);
        TextView t = Ui.text(c, title, 14, Ui.TEXT, false);
        TextView v = Ui.text(c, fmt.apply(p.getInt(key, def)), 14, Ui.ACCENT, true);
        row.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(v);
        s.addView(row, Ui.lp(-1, -2, Ui.dp(c, 10)));
        SeekBar b = new SeekBar(c);
        b.setMin(min);
        b.setMax(max);
        b.setProgress(p.getInt(key, def));
        b.setProgressTintList(ColorStateList.valueOf(Ui.ACCENT));
        b.setThumbTintList(ColorStateList.valueOf(Ui.ACCENT));
        b.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar sb, int val, boolean user) {
                v.setText(fmt.apply(val));
                if (user) {
                    p.set(key, val);
                    onChange.run();
                }
            }
            public void onStartTrackingTouch(SeekBar sb) {}
            public void onStopTrackingTouch(SeekBar sb) { p.save(); }
        });
        s.addView(b, Ui.lp(-1, -2, Ui.dp(c, 2)));
    }

    private void choice(LinearLayout s, String title, String key, String[] labels, int def) {
        s.addView(Ui.text(c, title, 14, Ui.TEXT, false), Ui.lp(-1, -2, Ui.dp(c, 10)));
        LinearLayout row = Ui.hbox(c);
        row.setBackground(Ui.round(c, Ui.CARD2, 12));
        int pd = Ui.dp(c, 3);
        row.setPadding(pd, pd, pd, pd);
        TextView[] items = new TextView[labels.length];
        Runnable paint = () -> {
            int cur = p.getInt(key, def);
            for (int i = 0; i < items.length; i++) {
                boolean sel = i == cur;
                items[i].setBackground(sel ? Ui.round(c, Ui.ACCENT, 10) : null);
                items[i].setTextColor(sel ? 0xFF0B0D10 : Ui.TEXT);
            }
        };
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            TextView t = Ui.text(c, labels[i], 14, Ui.TEXT, true);
            t.setGravity(Gravity.CENTER);
            t.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            int vp = Ui.dp(c, 8);
            t.setPadding(vp, vp, vp, vp);
            t.setOnClickListener(x -> {
                p.set(key, idx);
                p.save();
                paint.run();
                onChange.run();
            });
            items[i] = t;
            row.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        }
        paint.run();
        s.addView(row, Ui.lp(-1, -2, Ui.dp(c, 6)));
    }

    private void action(LinearLayout s, String label, Runnable r) {
        TextView b = Ui.button(c, label, Ui.CARD2, Ui.TEXT, v -> r.run());
        b.setTextSize(14);
        s.addView(b, Ui.lp(-1, -2, Ui.dp(c, 12)));
    }

    private void module(LinearLayout s, int i) {
        String id = K.MODS[i];
        LinearLayout card = Ui.vbox(c);
        card.setBackground(Ui.round(c, Ui.CARD2, 12));
        int pd = Ui.dp(c, 12);
        card.setPadding(pd, Ui.dp(c, 4), pd, pd);
        s.addView(card, Ui.lp(-1, -2, Ui.dp(c, 10)));

        LinearLayout details = Ui.vbox(c);
        Switch w = sw(card, K.MOD_NAMES[i], K.mod(id, "on"), K.MOD_ON[i]);
        w.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        details.setVisibility(w.isChecked() ? View.VISIBLE : View.GONE);
        w.setOnCheckedChangeListener((b, on) -> {
            p.set(K.mod(id, "on"), on);
            p.save();
            details.setVisibility(on ? View.VISIBLE : View.GONE);
            onChange.run();
        });
        card.addView(details);

        colors(details, K.mod(id, "color"), K.MOD_COLOR[i]);
        seek(details, "الحجم", K.mod(id, "size"), 50, 300, 100, v -> v + "%");
        if (i == K.CROSSHAIR) {
            choice(details, "الشكل", K.mod(id, "style"), new String[]{"+", "•", "◯", "✕"}, 0);
        } else {
            seek(details, "خلفية", K.mod(id, "bg"), 0, 255, 110, v -> Math.round(v * 100f / 255f) + "%");
        }
    }

    private void colors(LinearLayout s, String key, int def) {
        s.addView(Ui.text(c, "اللون", 14, Ui.TEXT, false), Ui.lp(-1, -2, Ui.dp(c, 8)));
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = Ui.hbox(c);
        hs.addView(row);
        int sz = Ui.dp(c, 30);
        Swatch[] sws = new Swatch[K.PALETTE.length];
        for (int i = 0; i < K.PALETTE.length; i++) {
            int col = K.PALETTE[i];
            Swatch v = new Swatch(c, col);
            sws[i] = v;
            v.setOnClickListener(x -> {
                p.set(key, col);
                p.save();
                for (Swatch o : sws) o.selected = o.color == col;
                for (Swatch o : sws) o.invalidate();
                onChange.run();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sz, sz);
            lp.rightMargin = lp.leftMargin = Ui.dp(c, 4);
            row.addView(v, lp);
        }
        int cur = p.getInt(key, def);
        for (Swatch o : sws) o.selected = o.color == cur;
        s.addView(hs, Ui.lp(-1, -2, Ui.dp(c, 6)));
    }

    /** Colour circle; colour 0 = animated rainbow. */
    static final class Swatch extends View {
        final int color;
        boolean selected;
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);

        Swatch(Context c, int color) {
            super(c);
            this.color = color;
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(Ui.dp(c, 2.5f));
            ring.setColor(Color.WHITE);
            setClickable(true);
        }

        @Override
        protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight(), r = Math.min(w, h) / 2f - Ui.dp(getContext(), 3);
            if (color == 0) {
                fill.setShader(new SweepGradient(w / 2, h / 2,
                        new int[]{0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000}, null));
            } else {
                fill.setShader(null);
                fill.setColor(color);
            }
            cv.drawCircle(w / 2, h / 2, r, fill);
            if (selected) {
                ring.setColor(color == 0xFFFFFFFF ? Ui.ACCENT : Color.WHITE);
                cv.drawCircle(w / 2, h / 2, r + Ui.dp(getContext(), 1), ring);
            }
        }
    }
}
