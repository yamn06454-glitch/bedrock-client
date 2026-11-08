package com.yamn.bclient.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.app.ActivityManager;
import android.graphics.Typeface;
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
import com.yamn.bclient.core.K;
import com.yamn.bclient.core.Trace;
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
    private LinearLayout crashCard;
    private TextView crashMsg;
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
        if (BuildConfig.DEV_SKIP_LEGIT) {
            TextView dev = Ui.text(this, "🧪 نسخة مطوّر للاختبار — فحص النسخة الأصلية متوقف. لا تنشرها.", 13, 0xFF0B0D10, true);
            dev.setBackground(Ui.round(this, 0xFFFACC15, 10));
            int dp8 = Ui.dp(this, 8);
            dev.setPadding(dp8, dp8, dp8, dp8);
            root.addView(dev, Ui.lp(-1, -2, Ui.dp(this, 10)));
        }

        // status card
        LinearLayout card = Ui.vbox(this);
        card.setBackground(Ui.round(this, Ui.CARD, 16));
        card.setPadding(pad, pad, pad, pad);
        mcTitle = Ui.text(this, "", 17, Ui.TEXT, true);
        mcSub = Ui.text(this, "", 13, Ui.SUB, false);
        card.addView(mcTitle);
        card.addView(mcSub, Ui.lp(-1, -2, Ui.dp(this, 4)));
        root.addView(card, Ui.lp(-1, -2, Ui.dp(this, 18)));

        // last-crash card (hidden unless the previous launch died)
        crashCard = Ui.vbox(this);
        crashCard.setBackground(Ui.round(this, 0xFF3A1518, 16));
        crashCard.setPadding(pad, pad, pad, pad);
        crashCard.addView(Ui.text(this, "⚠️ اللعبة طفت في آخر تشغيل", 16, 0xFFFCA5A5, true));
        crashMsg = Ui.text(this, "", 12, Ui.TEXT, false);
        crashCard.addView(crashMsg, Ui.lp(-1, -2, Ui.dp(this, 6)));
        LinearLayout cr = Ui.hbox(this);
        int m4 = Ui.dp(this, 4);
        cr.addView(Ui.button(this, "📋 عرض السجل", Ui.CARD2, Ui.TEXT, v -> showLog()), Ui.weight(1, m4));
        cr.addView(Ui.button(this, "🛡️ شغّل بدون hooks", Ui.CARD2, Ui.TEXT, v -> {
            p.set(K.HOOKS, false);
            p.save();
            reloadUi();
            onLaunch();
        }), Ui.weight(1, m4));
        crashCard.addView(cr, Ui.lp(-1, -2, Ui.dp(this, 10)));
        crashCard.setVisibility(View.GONE);
        root.addView(crashCard, Ui.lp(-1, -2, Ui.dp(this, 12)));

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

        TextView logBtn = Ui.button(this, "📋 سجل آخر تشغيل", Ui.CARD, Ui.TEXT, v -> showLog());
        root.addView(logBtn, Ui.lp(-1, -2, Ui.dp(this, 20)));

        TextView reset = Ui.button(this, "🧹 إعادة تجهيز ملفات اللعبة", Ui.CARD, Ui.TEXT, v -> {
            if (busy) return;
            Preparer.clearCache(this);
            Toast.makeText(this, "تم المسح، بيتجهز من جديد عند التشغيل", Toast.LENGTH_SHORT).show();
        });
        root.addView(reset, Ui.lp(-1, -2, Ui.dp(this, 8)));

        TextView foot = Ui.text(this, "يشغّل نسختك الأصلية من ماين كرافت (Google Play) بدون تعديل ملفاتها.\nعوالم اللانشر منفصلة عن تطبيق ماين كرافت الأصلي.", 12, Ui.SUB, false);
        foot.setGravity(Gravity.CENTER);
        foot.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        root.addView(foot, Ui.lp(-1, -2, Ui.dp(this, 14)));

        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        reloadUi();
    }

    private void reloadUi() {
        p.reload();
        refreshStatus();
        refreshCrash();
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
            String src = "نسخة أصلية من Google Play";
            if (BuildConfig.DEV_SKIP_LEGIT) {
                if (info.devBypass) src = "🧪 نسخة غير أصلية (مسموحة لأنها نسخة مطوّر)";
            }
            mcSub.setText(src + (ready ? " • الملفات جاهزة" : " • بيتجهز أول تشغيل"));
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
        Trace.reset(getFilesDir(), "launcher: starting game • launcher v" + BuildConfig.VERSION_NAME
                + " • mc " + info.versionName + " • Android " + android.os.Build.VERSION.RELEASE
                + " • " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL
                + " • hooks " + (p.getBool(K.HOOKS, true) ? "on" : "off"));
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

    // ------------------------------------------------------------ crash diagnostics

    private boolean gameAlive() {
        try {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            for (ActivityManager.RunningAppProcessInfo pi : am.getRunningAppProcesses()) {
                if (pi.processName != null && pi.processName.endsWith(":game")) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private void refreshCrash() {
        String trace = Trace.read(Trace.traceFile(getFilesDir()));
        String crash = Trace.read(Trace.crashFile(getFilesDir()));
        boolean died = !crash.isEmpty()
                || (!trace.isEmpty() && !trace.contains(Trace.OK_MARK) && !gameAlive());
        crashCard.setVisibility(died ? View.VISIBLE : View.GONE);
        if (died) {
            String[] lines = trace.trim().split("\n");
            String last = lines.length > 0 ? lines[lines.length - 1] : "";
            crashMsg.setText("آخر خطوة: " + last + "\nاضغط \"عرض السجل\" وانسخه وأرسله للمطور.");
        }
    }

    private String collectLog() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Bedrock Client ").append(BuildConfig.VERSION_NAME).append(" ===\n");
        sb.append("Device: ").append(android.os.Build.MANUFACTURER).append(' ').append(android.os.Build.MODEL)
                .append(" • Android ").append(android.os.Build.VERSION.RELEASE)
                .append(" (SDK ").append(android.os.Build.VERSION.SDK_INT).append(")\n");
        sb.append("Minecraft: ").append(info == null ? "?" : info.versionName).append("\n\n");
        sb.append("--- launch trace ---\n").append(Trace.read(Trace.traceFile(getFilesDir()))).append('\n');
        String crash = Trace.read(Trace.crashFile(getFilesDir()));
        if (!crash.isEmpty()) sb.append("--- java crash ---\n").append(crash).append('\n');
        sb.append("--- logcat (game process) ---\n").append(logcat());
        return sb.toString();
    }

    /** Our own uid's log lines (no permission needed). Keeps lines from the game pid + crash tags. */
    private String logcat() {
        String trace = Trace.read(Trace.traceFile(getFilesDir()));
        String pid = null;
        java.util.regex.Matcher mm = java.util.regex.Pattern.compile("pid (\\d+)").matcher(trace);
        while (mm.find()) pid = mm.group(1);
        StringBuilder out = new StringBuilder();
        try {
            java.lang.Process pr = Runtime.getRuntime().exec(new String[]{"logcat", "-d", "-v", "threadtime", "-t", "30000"});
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(pr.getInputStream()));
            java.util.ArrayDeque<String> tail = new java.util.ArrayDeque<>();
            java.util.ArrayDeque<String> auth = new java.util.ArrayDeque<>();
            java.util.ArrayList<String> fatal = new java.util.ArrayList<>(); // head of a native/ART abort
            java.util.regex.Pattern authP = java.util.regex.Pattern.compile(
                    "(?i)xal|xbox|msa|sisu|auth|sign.?in|login|token|playfab|mooshroom|licens|integrity");
            String line;
            while ((line = r.readLine()) != null) {
                boolean mine = pid != null && line.matches("^\\S+\\s+\\S+\\s+" + pid + "\\s.*");
                boolean important = line.contains("AndroidRuntime") || line.contains("Fatal signal")
                        || line.contains("BClient") || line.contains(" F libc") || line.contains(" F DEBUG");
                if (mine && fatal.size() < 60 && (line.contains(" F ") || line.contains("Abort message")
                        || line.contains("JNI DETECTED") || line.contains("Check failed"))) {
                    fatal.add(line);
                }
                if (mine && authP.matcher(line).find() && !line.contains("runtime.cc")) {
                    auth.add(line);
                    if (auth.size() > 400) auth.poll();
                }
                if (mine || important) {
                    tail.add(line);
                    if (tail.size() > 300) tail.poll();
                }
            }
            if (!fatal.isEmpty()) {
                out.append(">> abort (first lines):\n");
                for (String l : fatal) out.append(l).append('\n');
                out.append('\n');
            }
            out.append(">> sign-in / Xbox lines:\n");
            for (String l : auth) out.append(l).append('\n');
            out.append("\n>> last lines:\n");
            for (String l : tail) out.append(l).append('\n');
        } catch (Throwable t) {
            out.append("logcat failed: ").append(t);
        }
        return out.length() == 0 ? "(empty)\n" : out.toString();
    }

    private void showLog() {
        progressTv.setVisibility(View.VISIBLE);
        progressTv.setText("جاري تجميع السجل…");
        new Thread(() -> {
            String log = collectLog();
            runOnUiThread(() -> {
                progressTv.setVisibility(View.GONE);
                TextView tv = new TextView(this);
                tv.setText(log);
                tv.setTextIsSelectable(true);
                tv.setTypeface(Typeface.MONOSPACE);
                tv.setTextSize(10);
                tv.setTextColor(Ui.TEXT);
                tv.setTextDirection(View.TEXT_DIRECTION_LTR);
                int pd = Ui.dp(this, 12);
                tv.setPadding(pd, pd, pd, pd);
                android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
                hs.addView(tv);
                ScrollView sv = new ScrollView(this);
                sv.addView(hs);
                new AlertDialog.Builder(this)
                        .setTitle("سجل آخر تشغيل")
                        .setView(sv)
                        .setPositiveButton("📋 نسخ", (d, w) -> {
                            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                            cm.setPrimaryClip(ClipData.newPlainText("bedrock-client-log", log));
                            Toast.makeText(this, "انسخ ✓ — الصقه في المحادثة", Toast.LENGTH_SHORT).show();
                        })
                        .setNeutralButton("مشاركة", (d, w) -> {
                            Intent i = new Intent(Intent.ACTION_SEND);
                            i.setType("text/plain");
                            i.putExtra(Intent.EXTRA_TEXT, log);
                            startActivity(Intent.createChooser(i, "مشاركة السجل"));
                        })
                        .setNegativeButton("إغلاق", null)
                        .show();
            });
        }, "collect-log").start();
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
