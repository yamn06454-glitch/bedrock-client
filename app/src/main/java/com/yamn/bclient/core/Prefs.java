package com.yamn.bclient.core;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Properties;

/**
 * Tiny file-backed settings store. Unlike SharedPreferences it can be re-read,
 * so changes made inside the game process show up in the launcher and vice versa.
 */
public final class Prefs {
    private static Prefs inst;
    private final File file;
    private final Properties p = new Properties();

    private Prefs(File f) {
        file = f;
        reload();
    }

    public static synchronized Prefs get(Context c) {
        if (inst == null) inst = new Prefs(new File(c.getFilesDir(), "client.properties"));
        return inst;
    }

    public synchronized void reload() {
        p.clear();
        if (!file.exists()) return;
        try (FileInputStream in = new FileInputStream(file)) {
            p.load(in);
        } catch (Exception ignored) {
        }
    }

    public synchronized void save() {
        try {
            file.getParentFile().mkdirs();
            File tmp = new File(file.getPath() + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                p.store(out, null);
            }
            tmp.renameTo(file);
        } catch (Exception ignored) {
        }
    }

    public synchronized boolean getBool(String k, boolean d) {
        String v = p.getProperty(k);
        return v == null ? d : Boolean.parseBoolean(v);
    }

    public synchronized int getInt(String k, int d) {
        try {
            String v = p.getProperty(k);
            return v == null ? d : Integer.parseInt(v);
        } catch (Exception e) {
            return d;
        }
    }

    public synchronized float getFloat(String k, float d) {
        try {
            String v = p.getProperty(k);
            return v == null ? d : Float.parseFloat(v);
        } catch (Exception e) {
            return d;
        }
    }

    public synchronized String getStr(String k, String d) {
        return p.getProperty(k, d);
    }

    public synchronized void set(String k, Object v) {
        p.setProperty(k, String.valueOf(v));
    }

    /** Stable text of all values under a prefix (used to detect changes). */
    public synchronized String dump(String prefix) {
        java.util.TreeMap<String, String> m = new java.util.TreeMap<>();
        for (String k : p.stringPropertyNames()) if (k.startsWith(prefix)) m.put(k, p.getProperty(k));
        return m.toString();
    }

    public synchronized void removePrefix(String prefix) {
        ArrayList<String> rm = new ArrayList<>();
        for (String k : p.stringPropertyNames()) if (k.startsWith(prefix)) rm.add(k);
        for (String k : rm) p.remove(k);
    }
}
