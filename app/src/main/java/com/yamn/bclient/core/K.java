package com.yamn.bclient.core;

/** Setting keys + HUD module definitions. */
public final class K {
    private K() {}

    // Zoom
    public static final String ZOOM_ON = "zoom.on";
    public static final String ZOOM_MODE = "zoom.mode";       // 0 real FOV, 1 digital
    public static final String ZOOM_LEVEL = "zoom.level";     // x10 (40 = 4.0x)
    public static final String ZOOM_HOLD = "zoom.hold";       // 0 hold, 1 toggle
    public static final String ZOOM_SMOOTH = "zoom.smooth";

    // Floating buttons
    public static final String BTN_BACK = "btn.back";
    public static final String BTN_SHOT = "btn.shot";
    public static final String BTN_SIZE = "btn.size";         // percent
    public static final String BTN_ALPHA = "btn.alpha";       // 0..255
    public static final String BTN_LOCK = "btn.lock";
    public static final String BTN_POS = "btn.pos.";

    // HUD global
    public static final String HUD_ON = "hud.on";
    public static final String HUD_FONT = "hud.font";         // 0 normal 1 bold 2 mono
    public static final String HUD_SHADOW = "hud.shadow";
    public static final String HUD_RADIUS = "hud.radius";     // dp
    public static final String HUD_TEXT = "hud.text.value";

    // Game
    public static final String RESTART_BTN = "game.restartBtn";
    public static final String RESTART_SECS = "game.restartSecs";
    public static final String HI_REFRESH = "game.hiRefresh";
    public static final String KEEP_ON = "game.keepOn";

    // HUD modules
    public static final String[] MODS = {"fps", "clock", "battery", "ram", "session", "text", "crosshair"};
    public static final String[] MOD_NAMES = {"عداد FPS", "الساعة", "البطارية", "الرام", "مدة اللعب", "نص مخصص", "كروس هير مخصص"};
    public static final boolean[] MOD_ON = {true, true, false, false, false, false, false};
    /** 0 = rainbow */
    public static final int[] MOD_COLOR = {0xFF4ADE80, 0xFFFFFFFF, 0xFFFACC15, 0xFF60A5FA, 0xFFF472B6, 0xFFFFFFFF, 0xFFFFFFFF};
    public static final int CROSSHAIR = 6;

    public static String mod(String id, String field) { return "hud." + id + "." + field; }

    public static float defX(int i) { return 0.015f; }
    public static float defY(int i) { return 0.16f + i * 0.065f; }

    public static final int[] PALETTE = {
            0xFFFFFFFF, 0xFFEF4444, 0xFFF97316, 0xFFFACC15, 0xFF4ADE80,
            0xFF22D3EE, 0xFF60A5FA, 0xFFA78BFA, 0xFFF472B6, 0xFF9CA3AF, 0xFF111111, 0
    };
}
