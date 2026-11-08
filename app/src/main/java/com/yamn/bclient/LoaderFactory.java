package com.yamn.bclient;

import android.app.AppComponentFactory;
import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.util.Log;

import com.yamn.bclient.core.GameEnv;
import com.yamn.bclient.core.Trace;

import java.io.File;
import java.io.FileInputStream;

import dalvik.system.PathClassLoader;

/**
 * Our own loader. In the :game process we replace the app class loader with one
 * that sees our classes + the installed Minecraft's dex + its extracted native libs.
 * This is what lets GameActivity extend Minecraft's real MainActivity.
 */
public class LoaderFactory extends AppComponentFactory {
    private static final String TAG = "BClient";

    @Override
    public ClassLoader instantiateClassLoader(ClassLoader cl, ApplicationInfo ai) {
        try {
            String proc = processName();
            if (proc == null || !proc.endsWith(":game")) return cl;

            File files = new File(ai.dataDir, "files");
            Trace.mark(files, "factory: process " + proc + " pid " + android.os.Process.myPid());
            GameEnv.State st = GameEnv.read(files);
            if (st == null || !new File(st.mcApk).exists()) {
                Log.e(TAG, "game state missing - open the launcher first");
                Trace.mark(files, "✗ factory: game state missing / Minecraft path changed");
                return cl;
            }

            StringBuilder dex = new StringBuilder(ai.sourceDir);
            if (ai.splitSourceDirs != null) {
                for (String s : ai.splitSourceDirs) dex.append(File.pathSeparatorChar).append(s);
            }
            for (String d : st.dexApks) dex.append(File.pathSeparatorChar).append(d);

            String libs = ai.nativeLibraryDir + File.pathSeparator + st.libDir;
            Log.i(TAG, "game classloader: mc=" + st.version + " abi=" + st.abi);
            // Parent = boot loader, so our classes and Minecraft's live in the same loader.
            ClassLoader gl = new PathClassLoader(dex.toString(), libs, cl.getParent());
            try {
                gl.loadClass("com.mojang.minecraftpe.MainActivity");
                Trace.mark(files, "factory: classloader ok (mc " + st.version + ", " + st.abi + ")");
            } catch (Throwable t) {
                Trace.mark(files, "✗ factory: MainActivity not found in Minecraft dex: " + t);
            }
            return gl;
        } catch (Throwable t) {
            Log.e(TAG, "instantiateClassLoader failed", t);
            try {
                Trace.mark(new File(ai.dataDir, "files"), "✗ factory failed: " + Trace.stack(t));
            } catch (Throwable ignored) {
            }
            return cl;
        }
    }

    private static String processName() {
        try {
            String n = Application.getProcessName();
            if (n != null && !n.isEmpty()) return n;
        } catch (Throwable ignored) {
        }
        try (FileInputStream in = new FileInputStream("/proc/self/cmdline")) {
            byte[] b = new byte[256];
            int n = in.read(b);
            int len = 0;
            while (len < n && b[len] != 0) len++;
            return new String(b, 0, len);
        } catch (Throwable t) {
            return null;
        }
    }
}
