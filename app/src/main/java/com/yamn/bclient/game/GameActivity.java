package com.yamn.bclient.game;

import android.content.pm.ApplicationInfo;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.Display;
import android.view.WindowManager;

import com.mojang.minecraftpe.MainActivity;
import com.yamn.bclient.App;
import com.yamn.bclient.core.GameEnv;
import com.yamn.bclient.core.K;
import com.yamn.bclient.core.Prefs;

import java.io.File;

/**
 * Extends the REAL Minecraft MainActivity (loaded at runtime from the installed game
 * by LoaderFactory). Runs only in the :game process.
 */
public class GameActivity extends MainActivity {
    private Overlay overlay;
    private ApplicationInfo fakeInfo;

    @Override
    public void onCreate(Bundle b) {
        Prefs p = Prefs.get(this);
        p.reload();
        if (p.getBool(K.KEEP_ON, true)) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (p.getBool(K.HI_REFRESH, true)) maxRefreshRate();
        super.onCreate(b);
        overlay = new Overlay(this);
    }

    private void maxRefreshRate() {
        try {
            Display d = getWindowManager().getDefaultDisplay();
            Display.Mode cur = d.getMode(), best = cur;
            for (Display.Mode m : d.getSupportedModes()) {
                if (m.getPhysicalWidth() == cur.getPhysicalWidth()
                        && m.getPhysicalHeight() == cur.getPhysicalHeight()
                        && m.getRefreshRate() > best.getRefreshRate()) best = m;
            }
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.preferredDisplayModeId = best.getModeId();
            getWindow().setAttributes(lp);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (overlay != null) getWindow().getDecorView().post(overlay::attach);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (overlay != null) overlay.onResume();
    }

    @Override
    public void onDestroy() {
        if (overlay != null) overlay.detach();
        super.onDestroy();
    }

    // ---- Make Minecraft see its own APK, resources and assets ----

    @Override
    public Resources getResources() {
        Resources r = App.mcResources();
        return r != null ? r : super.getResources();
    }

    @Override
    public AssetManager getAssets() {
        Resources r = App.mcResources();
        return r != null ? r.getAssets() : super.getAssets();
    }

    @Override
    public ApplicationInfo getApplicationInfo() {
        ApplicationInfo real = super.getApplicationInfo();
        if (fakeInfo != null) return fakeInfo;
        ApplicationInfo mc = App.mcInfo();
        if (real == null || mc == null) return real;
        GameEnv.State st = GameEnv.read(new File(real.dataDir, "files"));
        if (st == null) return real;
        ApplicationInfo f = new ApplicationInfo(real);
        f.sourceDir = mc.sourceDir;
        f.publicSourceDir = mc.publicSourceDir;
        f.splitNames = mc.splitNames;
        f.splitSourceDirs = mc.splitSourceDirs;
        f.splitPublicSourceDirs = mc.splitPublicSourceDirs;
        f.nativeLibraryDir = st.libDir;
        fakeInfo = f;
        return f;
    }

    @Override
    public String getPackageCodePath() {
        ApplicationInfo mc = App.mcInfo();
        return mc != null ? mc.sourceDir : super.getPackageCodePath();
    }

    @Override
    public String getPackageResourcePath() {
        ApplicationInfo mc = App.mcInfo();
        return mc != null ? mc.sourceDir : super.getPackageResourcePath();
    }
}
