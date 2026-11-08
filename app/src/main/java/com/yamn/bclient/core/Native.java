package com.yamn.bclient.core;

import android.util.Log;

/** Bridge to libclient.so (render hooks: zoom + frame counter). */
public final class Native {
    private static boolean loaded;
    private static String status = "not loaded";

    private Native() {}

    public static synchronized void load() {
        if (loaded) return;
        try {
            System.loadLibrary("client");
            status = init();
            loaded = true;
            Log.i("BClient", "hooks: " + status);
        } catch (Throwable t) {
            status = "load failed: " + t;
            Log.e("BClient", "native load failed", t);
        }
    }

    public static boolean isLoaded() { return loaded; }
    public static String status() { return status; }

    public static void zoom(float z) { if (loaded) setZoom(z); }
    public static void mode(int m) { if (loaded) setMode(m); }
    public static void smooth(boolean s) { if (loaded) setSmooth(s); }
    public static long frameCount() { return loaded ? frames() : 0; }
    /** 0 = unknown, 1 = OpenGL ES, 2 = Vulkan */
    public static int renderBackend() { return loaded ? backend() : 0; }

    private static native String init();
    private static native void setZoom(float z);
    private static native void setMode(int mode);
    private static native void setSmooth(boolean smooth);
    private static native long frames();
    private static native int backend();
}
