package com.yamn.bclient.game;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.provider.MediaStore;
import android.view.KeyEvent;
import android.view.PixelCopy;
import android.view.View;
import android.widget.Toast;

import com.yamn.bclient.ui.RestartActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class Actions {
    private Actions() {}

    /** Sends a real Back key to the game window (our overlays never take focus). */
    static void back(Activity a) {
        new Thread(() -> {
            try {
                new Instrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            } catch (Throwable t) {
                toast(a, "ما قدرت أرسل زر الرجوع");
            }
        }, "back-key").start();
    }

    /** Kills the game process and starts it again (works from the loading screen too). */
    static void restart(Activity a) {
        Intent i = new Intent(a, RestartActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        a.startActivity(i);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            a.finishAndRemoveTask();
            Process.killProcess(Process.myPid());
        }, 250);
    }

    static void screenshot(Activity a) {
        View d = a.getWindow().getDecorView();
        int w = d.getWidth(), h = d.getHeight();
        if (w <= 0 || h <= 0) return;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        try {
            PixelCopy.request(a.getWindow(), bmp, res -> {
                if (res == PixelCopy.SUCCESS) new Thread(() -> save(a, bmp), "shot-save").start();
                else toast(a, "فشلت لقطة الشاشة (" + res + ")");
            }, new Handler(Looper.getMainLooper()));
        } catch (Throwable t) {
            toast(a, "لقطة الشاشة غير مدعومة هنا");
        }
    }

    private static void save(Activity a, Bitmap bmp) {
        String name = "BC_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".png";
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                v.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                v.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/BedrockClient");
                Uri uri = a.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
                if (uri == null) throw new IllegalStateException("no uri");
                try (OutputStream os = a.getContentResolver().openOutputStream(uri)) {
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, os);
                }
                toast(a, "📸 انحفظت في الصور/BedrockClient");
            } else {
                File dir = a.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
                File f = new File(dir, name);
                try (OutputStream os = new FileOutputStream(f)) {
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, os);
                }
                toast(a, "📸 انحفظت: " + f.getAbsolutePath());
            }
        } catch (Throwable t) {
            toast(a, "ما قدرت أحفظ الصورة");
        } finally {
            bmp.recycle();
        }
    }

    static void toast(Activity a, String msg) {
        a.runOnUiThread(() -> Toast.makeText(a, msg, Toast.LENGTH_SHORT).show());
    }
}
