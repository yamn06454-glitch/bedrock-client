package com.yamn.bclient.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.yamn.bclient.BuildConfig;
import com.yamn.bclient.core.GameEnv;
import com.yamn.bclient.core.McCheck;
import com.yamn.bclient.core.Preparer;
import com.yamn.bclient.core.Prefs;

/** Launcher screen (main process). Never touches Minecraft classes directly. */
public class MainActivity extends Activity {
    public static final String GAME_ACTIVITY = "com.yamn.bclient.game.GameActivity";

    private Prefs p;
    private McCheck.Info info;
    private TextView mcTitle, mcSub, progressTv, launchBtn;
    private ProgressBar progress;
    private FrameLayout settingsHost;
    private volatile boolean busy;
    private int lastPct = -1;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        p = Prefs.get(this);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Ui.BG);
        sv.setFillViewport(true);
        LinearLayout root = Ui.vbox(this);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        int pad = Ui.dp(this, 16);
        root.setPadding(pad, pad * 2, pad, pad * 2);
        sv.addView(root);

        TextView title = Ui.text(this, "Bedrock Client", 30, Ui.TEXT, true);
        root.addView(title);
        root.addView(Ui.text(this, "لانشر خاص لماين كرافت بيدروك • v" + BuildConfig.VERSION_NAME, 13, Ui.SUB, false));

        // status card
        LinearLayout card = Ui.vbox(this);
        card.setBackground(Ui.round(this, Ui.CARD, 16));
        card.setPadding(pad, pad, pad, pad);
        mcTitle = Ui.text(this, "", 17, Ui.TEXT, true);
        mcSub = Ui.text(this, "", 13, Ui.SUB, false);
        card.addView(mcTitle);
        card.addView(mcSub, Ui.lp(-1, -2, Ui.dp(this, 4)));
        root.addView(card, Ui.lp(-1, -2, Ui.dp(this, 18)));

        launchBtn = Ui.button(this, "▶  تشغيل اللعبة", Ui.ACCENT, 0xFF0B0D10, v -> onLaunch());
        launchBtn.setTextSize(19);
        root.addView(launchBtn, Ui.lp(-1, Ui.dp(this, 60), Ui.dp(this, 14)));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
        progress.setVisibility(View.GONE);
        root.addView(progress, Ui.lp(-1, -2, Ui.dp(this, 10)));
        progressTv = Ui.text(this, "", 13, Ui.SUB, false);
        progressTv.setVisibility(View.GONE);
        root.addView(progressTv);

        TextView st = Ui.text(this, "الإعدادات", 20, Ui.TEXT, true);
        root.addView(st, Ui.lp(-1, -2, Ui.dp(this, 22)));
        root.addView(Ui.text(this, "كل ميزة تقدر تطفيها وتشغلها، وتقدر تعدلها كمان من داخل اللعبة بزر ☰.", 13, Ui.SUB, false));

        settingsHost = new FrameLayout(this);
        root.addView(settingsHost, Ui.lp(-1, -2, 0));

        TextView reset = Ui.button(this, "🧹 إعادة تجهيز ملفات اللعبة", Ui.CARD, Ui.TEXT, v -> {
            if (busy) return;
            Preparer.clearCache(this);
            Toast.makeText(this, "تم المسح، بيتجهز من جديد عند التشغيل", Toast.LENGTH_SHORT).show();
        });
        root.addView(reset, Ui.lp(-1, -2, Ui.dp(this, 20)));

        TextView foot = Ui.text(this, "يشغّل نسختك الأصلية من ماين كرافت (Google Play) بدون تعديل ملفاتها.\nعوالم اللانشر منفصلة عن تطبيق ماين كرافت الأصلي.", 12, Ui.SUB, false);
        foot.setGravity(Gravity.CENTER);
        foot.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        root.addView(foot, Ui.lp(-1, -2, Ui.dp(this, 14)));

        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        p.reload();
        refreshStatus();
        settingsHost.removeAllViews();
        settingsHost.addView(SettingsUi.build(this, p, false, null));
    }

    private void refreshStatus() {
        info = McCheck.check(this);
        if (!info.installed) {
            mcTitle.setText("❌ ماين كرافت غير مثبتة");
            mcSub.setText("ثبّت Minecraft من Google Play أول.");
            launchBtn.setText("فتح Google Play");
        } else if (!info.legit) {
            mcTitle.setText("⛔ Minecraft " + info.versionName + " — نسخة غير أصلية");
            mcSub.setText("اللانشر يشتغل بس مع النسخة الأصلية المثبتة من Google Play.\nالمثبِّت الحالي: "
                    + (info.installer == null ? "غير معروف" : info.installer));
            launchBtn.setText("فتح Google Play");
        } else {
            mcTitle.setText("✅ Minecraft " + info.versionName);
            GameEnv.State st = GameEnv.read(getFilesDir());
            boolean ready = st != null && info.versionName.equals(st.version);
            mcSub.setText("نسخة أصلية من Google Play" + (ready ? " • الملفات جاهزة" : " • بيتجهز أول تشغيل"));
            launchBtn.setText("▶  تشغيل اللعبة");
        }
    }

    private void onLaunch() {
        if (busy) return;
        if (!info.installed || !info.legit) {
            openStore();
            return;
        }
        busy = true;
        lastPct = -1;
        launchBtn.setAlpha(0.5f);
        progress.setVisibility(View.VISIBLE);
        progressTv.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                Preparer.prepare(this, (pct, msg) -> {
                    if (pct == lastPct) return;
                    lastPct = pct;
                    runOnUiThread(() -> {
                        progress.setProgress(pct);
                        progressTv.setText(msg + "  " + pct + "%");
                    });
                });
                runOnUiThread(this::startGame);
            } catch (Throwable t) {
                runOnUiThread(() -> {
                    done();
                    new AlertDialog.Builder(this)
                            .setTitle("ما قدرت أجهز اللعبة")
                            .setMessage(String.valueOf(t.getMessage() == null ? t : t.getMessage()))
                            .setPositiveButton("تمام", null)
                            .show();
                });
            }
        }, "prepare").start();
    }

    private void startGame() {
        done();
        Intent i = new Intent();
        i.setClassName(getPackageName(), GAME_ACTIVITY); // by name: class only loadable in :game
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }

    private void done() {
        busy = false;
        launchBtn.setAlpha(1f);
        progress.setVisibility(View.GONE);
        progressTv.setVisibility(View.GONE);
        refreshStatus();
    }

    private void openStore() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + GameEnv.MC_PKG)));
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=" + GameEnv.MC_PKG)));
        }
    }
}
