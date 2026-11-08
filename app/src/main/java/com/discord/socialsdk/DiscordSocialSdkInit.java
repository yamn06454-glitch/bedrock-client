package com.discord.socialsdk;

import android.app.Activity;

/**
 * Stub for the private Discord Social SDK so the client builds without it.
 * Discord presence stays disabled (DISCORD_APPLICATION_ID = 0).
 */
public final class DiscordSocialSdkInit {
    private DiscordSocialSdkInit() {}

    public static void setEngineActivity(Activity activity) {}
}
