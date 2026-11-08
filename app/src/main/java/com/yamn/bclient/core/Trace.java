package com.yamn.bclient.core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Launch breadcrumbs written by the :game process, read by the launcher.
 * Tells us how far the game got even when it dies with a native crash.
 * No Context dependency (used from LoaderFactory before the app exists).
 */
public final class Trace {
    public static final String OK_MARK = "✓ first frame";

    private Trace() {}

    public static File traceFile(File filesDir) { return new File(filesDir, "game_trace.txt"); }
    public static File crashFile(File filesDir) { return new File(filesDir, "game_crash.txt"); }

    public static synchronized void reset(File filesDir, String header) {
        crashFile(filesDir).delete();
        write(traceFile(filesDir), stamp() + " " + header + "\n", false);
    }

    public static synchronized void mark(File filesDir, String msg) {
        write(traceFile(filesDir), stamp() + " " + msg + "\n", true);
    }

    public static synchronized void crash(File filesDir, String where, Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        write(crashFile(filesDir), stamp() + " CRASH in " + where + "\n" + sw + "\n", true);
        mark(filesDir, "✗ crash in " + where + ": " + t);
    }

    public static String read(File f) {
        try {
            if (!f.exists()) return "";
            return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return "";
        }
    }

    public static String stack(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private static String stamp() {
        return new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date());
    }

    private static void write(File f, String s, boolean append) {
        try {
            f.getParentFile().mkdirs();
            try (FileOutputStream o = new FileOutputStream(f, append)) {
                o.write(s.getBytes(StandardCharsets.UTF_8));
                o.getFD().sync();
            }
        } catch (Throwable ignored) {
        }
    }
}
