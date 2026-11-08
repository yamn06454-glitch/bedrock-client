package com.yamn.bclient.core;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Prepares the installed Minecraft for launching inside our :game process:
 * extracts its native libs (once per version) and records where its code lives.
 */
public final class Preparer {
    public interface Progress {
        void on(int percent, String msg);
    }

    private Preparer() {}

    public static GameEnv.State prepare(Context c, Progress pr) throws Exception {
        PackageManager pm = c.getPackageManager();
        PackageInfo pi = pm.getPackageInfo(GameEnv.MC_PKG, 0);
        ApplicationInfo ai = pi.applicationInfo;

        List<String> apks = new ArrayList<>();
        apks.add(ai.sourceDir);
        if (ai.splitSourceDirs != null) for (String s : ai.splitSourceDirs) apks.add(s);

        String key = pi.getLongVersionCode() + "_" + pi.lastUpdateTime;
        File filesDir = c.getFilesDir();
        File root = new File(filesDir, "mc");
        root.mkdirs();
        File libDir = new File(root, "lib_" + key);

        GameEnv.State old = GameEnv.read(filesDir);
        if (old != null && key.equals(old.key) && new File(libDir, ".done").exists()
                && new File(old.mcApk).exists()) {
            pr.on(100, "جاهز");
            return old;
        }

        pr.on(1, "تنظيف الملفات القديمة…");
        File[] kids = root.listFiles();
        if (kids != null) for (File f : kids) {
            if (f.isDirectory() && f.getName().startsWith("lib_")) deleteRec(f);
        }
        libDir.mkdirs();

        pr.on(3, "فحص المعمارية…");
        String abi = pickAbi(apks);

        // collect lib entries
        String prefix = "lib/" + abi + "/";
        long total = 0;
        List<String[]> jobs = new ArrayList<>(); // {apk, entryName}
        List<String> dexApks = new ArrayList<>();
        for (String apk : apks) {
            try (ZipFile z = new ZipFile(apk)) {
                if (z.getEntry("classes.dex") != null) dexApks.add(apk);
                Enumeration<? extends ZipEntry> en = z.entries();
                while (en.hasMoreElements()) {
                    ZipEntry e = en.nextElement();
                    String n = e.getName();
                    if (n.startsWith(prefix) && n.endsWith(".so") && n.indexOf('/', prefix.length()) < 0) {
                        jobs.add(new String[]{apk, n});
                        total += Math.max(e.getSize(), 1);
                    }
                }
            }
        }
        if (dexApks.isEmpty()) throw new IllegalStateException("ما لقيت كود اللعبة (classes.dex) داخل ملفات ماين كرافت");

        long done = 0;
        byte[] buf = new byte[1 << 16];
        boolean haveMain = false;
        for (String[] j : jobs) {
            try (ZipFile z = new ZipFile(j[0])) {
                ZipEntry e = z.getEntry(j[1]);
                String name = j[1].substring(prefix.length());
                if (name.equals("libminecraftpe.so")) haveMain = true;
                File out = new File(libDir, name);
                try (InputStream in = z.getInputStream(e); OutputStream os = new FileOutputStream(out)) {
                    int r;
                    while ((r = in.read(buf)) > 0) {
                        os.write(buf, 0, r);
                        done += r;
                        pr.on(5 + (int) (90 * done / Math.max(total, 1)), "استخراج " + name);
                    }
                }
            }
        }
        if (!haveMain) throw new IllegalStateException("libminecraftpe.so غير موجود");

        GameEnv.State st = new GameEnv.State();
        st.key = key;
        st.version = pi.versionName;
        st.abi = abi;
        st.mcApk = ai.sourceDir;
        st.libDir = libDir.getAbsolutePath();
        st.splits = ai.splitSourceDirs == null ? new String[0] : ai.splitSourceDirs;
        st.dexApks = dexApks.toArray(new String[0]);
        new File(libDir, ".done").createNewFile();
        GameEnv.write(filesDir, st);
        pr.on(100, "جاهز");
        return st;
    }

    private static String pickAbi(List<String> apks) throws Exception {
        String[] wanted = Process.is64Bit() ? Build.SUPPORTED_64_BIT_ABIS : Build.SUPPORTED_32_BIT_ABIS;
        for (String abi : wanted) {
            for (String apk : apks) {
                try (ZipFile z = new ZipFile(apk)) {
                    if (z.getEntry("lib/" + abi + "/libminecraftpe.so") != null) return abi;
                }
            }
        }
        throw new IllegalStateException(Process.is64Bit()
                ? "نسخة ماين كرافت المثبتة 32-bit بينما الجهاز يشغل اللانشر 64-bit"
                : "نسخة ماين كرافت المثبتة 64-bit بينما اللانشر يشتغل 32-bit");
    }

    public static void clearCache(Context c) {
        deleteRec(new File(c.getFilesDir(), "mc"));
    }

    private static void deleteRec(File f) {
        File[] k = f.listFiles();
        if (k != null) for (File x : k) deleteRec(x);
        f.delete();
    }
}
