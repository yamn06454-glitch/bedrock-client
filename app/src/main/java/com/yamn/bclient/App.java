package com.yamn.bclient;

import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.util.Log;

import com.yamn.bclient.core.GameEnv;
import com.yamn.bclient.core.K;
import com.yamn.bclient.core.Native;
import com.yamn.bclient.core.Prefs;
import com.yamn.bclient.core.Trace;

public class App extends Application {
    private static boolean game;
    private static Resources mcRes;
    private static ApplicationInfo mcInfo;

    public static boolean isGameProcess() { return game; }
    public static Resources mcResources() { return mcRes; }
    public static ApplicationInfo mcInfo() { return mcInfo; }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        String pn = Application.getProcessName();
        game = pn != null && pn.endsWith(":game");
        if (game) {
            final java.io.File files = base.getFilesDir();
            Trace.mark(files, "app: attach, loader=" + App.class.getClassLoader().getClass().getSimpleName());
            final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler((th, ex) -> {
                Trace.crash(files, "thread " + th.getName(), ex);
                if (prev != null) prev.uncaughtException(th, ex);
            });
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (!game) return;
        java.io.File files = getFilesDir();
        try {
            PackageManager pm = getPackageManager();
            mcInfo = pm.getApplicationInfo(GameEnv.MC_PKG, 0);
            mcRes = pm.getResourcesForApplication(mcInfo);
            Trace.mark(files, "app: Minecraft resources ok");
        } catch (Throwable t) {
            Log.e("BClient", "cannot load Minecraft resources", t);
            Trace.mark(files, "✗ app: Minecraft resources failed: " + t);
        }
        // Install render hooks before Minecraft creates its GL/Vulkan context.
        if (Prefs.get(this).getBool(K.HOOKS, true)) {
            Trace.mark(files, "app: loading hooks…");
            Native.load();
            Trace.mark(files, "app: hooks -> " + Native.status());
        } else {
            Trace.mark(files, "app: hooks disabled (safe mode)");
        }
    }

    @Override
    public Resources getResources() {
        if (game && mcRes != null) return mcRes;
        return super.getResources();
    }

    @Override
    public AssetManager getAssets() {
        if (game && mcRes != null) return mcRes.getAssets();
        return super.getAssets();
    }
}
