package org.levimc.launcher.core.mods.inbuilt.overlay;

import android.app.Activity;
import android.os.SystemClock;
import android.widget.Toast;

import org.levimc.launcher.R;
import org.levimc.launcher.core.minecraft.MinecraftProcessRestarter;
import org.levimc.launcher.core.mods.inbuilt.model.ModIds;

/**
 * Bedrock Client: restart the game in one tap (tap twice to confirm).
 * The overlay is attached as soon as the game activity resumes, so it is usable on the loading screen.
 */
public class RestartGameOverlay extends BaseOverlayButton {
    private static final long CONFIRM_WINDOW_MS = 2500L;
    private long lastTapAt = 0L;

    public RestartGameOverlay(Activity activity) {
        super(activity);
    }

    @Override
    protected String getModId() {
        return ModIds.RESTART_GAME;
    }

    @Override
    protected int getIconResource() {
        return R.drawable.ic_restart_game;
    }

    @Override
    protected long getShowDelayMillis() {
        return 0L;
    }

    @Override
    protected void onButtonClick() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastTapAt > CONFIRM_WINDOW_MS) {
            lastTapAt = now;
            Toast.makeText(activity, R.string.restart_game_confirm, Toast.LENGTH_SHORT).show();
            return;
        }
        lastTapAt = 0L;
        Toast.makeText(activity, R.string.restart_game_restarting, Toast.LENGTH_SHORT).show();
        MinecraftProcessRestarter.INSTANCE.restartGame(activity);
    }
}
