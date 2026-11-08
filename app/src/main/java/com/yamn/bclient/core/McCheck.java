package com.yamn.bclient.core;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

/** Detects the installed Minecraft and whether it's the genuine Google Play copy. */
public final class McCheck {
    public static final String PLAY_STORE = "com.android.vending";

    private McCheck() {}

    public static final class Info {
        public boolean installed;
        public String versionName = "";
        public long versionCode;
        public String installer;
        public boolean legit;
    }

    @SuppressWarnings("deprecation")
    public static Info check(Context c) {
        Info i = new Info();
        PackageManager pm = c.getPackageManager();
        try {
            PackageInfo pi = pm.getPackageInfo(GameEnv.MC_PKG, 0);
            i.installed = true;
            i.versionName = pi.versionName == null ? "?" : pi.versionName;
            i.versionCode = pi.getLongVersionCode();
        } catch (PackageManager.NameNotFoundException e) {
            return i;
        }
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                i.installer = pm.getInstallSourceInfo(GameEnv.MC_PKG).getInstallingPackageName();
            } else {
                i.installer = pm.getInstallerPackageName(GameEnv.MC_PKG);
            }
        } catch (Throwable ignored) {
        }
        i.legit = PLAY_STORE.equals(i.installer);
        return i;
    }
}
