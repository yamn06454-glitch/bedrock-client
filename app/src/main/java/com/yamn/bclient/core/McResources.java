package com.yamn.bclient.core;

import android.content.res.Resources;

/**
 * Minecraft's resources, but lookups by name that use OUR package name
 * (Minecraft calls getIdentifier(name, type, getPackageName())) are redirected
 * to com.mojang.minecraftpe so they resolve against Minecraft's own resource table.
 */
public final class McResources extends Resources {
    private final String ours;

    @SuppressWarnings("deprecation")
    public McResources(Resources mc, String ourPackage) {
        super(mc.getAssets(), mc.getDisplayMetrics(), mc.getConfiguration());
        this.ours = ourPackage;
    }

    @Override
    public int getIdentifier(String name, String defType, String defPackage) {
        if (ours.equals(defPackage)) defPackage = GameEnv.MC_PKG;
        if (name != null && name.startsWith(ours + ":")) {
            name = GameEnv.MC_PKG + name.substring(ours.length());
        }
        return super.getIdentifier(name, defType, defPackage);
    }
}
