package com.yamn.bclient.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

/**
 * Prepared game state, shared between the launcher process (writer)
 * and the :game process (reader, before any Minecraft class is loaded).
 * Must not depend on Context or Minecraft classes.
 */
public final class GameEnv {
    public static final String MC_PKG = "com.mojang.minecraftpe";

    private GameEnv() {}

    public static final class State {
        public String key;        // versionCode_lastUpdateTime
        public String version;    // versionName
        public String abi;
        public String mcApk;      // base.apk of installed Minecraft
        public String libDir;     // extracted native libs
        public String[] splits = new String[0];   // split APK paths
        public String[] dexApks = new String[0];  // APKs that contain classes.dex
    }

    public static File stateFile(File filesDir) {
        return new File(new File(filesDir, "mc"), "state.properties");
    }

    public static State read(File filesDir) {
        File f = stateFile(filesDir);
        if (!f.exists()) return null;
        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream(f)) {
            p.load(in);
        } catch (Exception e) {
            return null;
        }
        State s = new State();
        s.key = p.getProperty("key");
        s.version = p.getProperty("version", "?");
        s.abi = p.getProperty("abi", "");
        s.mcApk = p.getProperty("mcApk");
        s.libDir = p.getProperty("libDir");
        s.splits = split(p.getProperty("splits", ""));
        s.dexApks = split(p.getProperty("dexApks", ""));
        if (s.key == null || s.mcApk == null || s.libDir == null) return null;
        return s;
    }

    public static void write(File filesDir, State s) throws Exception {
        File f = stateFile(filesDir);
        f.getParentFile().mkdirs();
        Properties p = new Properties();
        p.setProperty("key", s.key);
        p.setProperty("version", s.version == null ? "?" : s.version);
        p.setProperty("abi", s.abi == null ? "" : s.abi);
        p.setProperty("mcApk", s.mcApk);
        p.setProperty("libDir", s.libDir);
        p.setProperty("splits", String.join(":", s.splits));
        p.setProperty("dexApks", String.join(":", s.dexApks));
        File tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            p.store(out, "bedrock-client");
        }
        if (!tmp.renameTo(f)) throw new IllegalStateException("cannot write state");
    }

    private static String[] split(String v) {
        if (v == null || v.isEmpty()) return new String[0];
        return v.split(":");
    }
}
