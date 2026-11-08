package com.yamn.bclient;

import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.util.Log;

import com.yamn.bclient.core.GameEnv;
import com.yamn.bclient.core.Native;

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
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (!game) return;
        try {
            PackageManager pm = getPackageManager();
            mcInfo = pm.getApplicationInfo(GameEnv.MC_PKG, 0);
            mcRes = pm.getResourcesForApplication(mcInfo);
        } catch (Throwable t) {
            Log.e("BClient", "cannot load Minecraft resources", t);
        }
        // Install render hooks before Minecraft creates its GL/Vulkan context.
        Native.load();
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
