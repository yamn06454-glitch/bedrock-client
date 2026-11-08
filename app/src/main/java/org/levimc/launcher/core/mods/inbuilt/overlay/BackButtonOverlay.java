package org.levimc.launcher.core.mods.inbuilt.overlay;

import android.app.Activity;
import android.view.KeyEvent;

import org.levimc.launcher.R;
import org.levimc.launcher.core.mods.inbuilt.model.ModIds;

/** Bedrock Client: floating Back button that sends the Android back key to the game. */
public class BackButtonOverlay extends BaseOverlayButton {
    public BackButtonOverlay(Activity activity) {
        super(activity);
    }

    @Override
    protected String getModId() {
        return ModIds.BACK_BUTTON;
    }

    @Override
    protected int getIconResource() {
        return R.drawable.ic_back_button;
    }

    @Override
    protected void onButtonClick() {
        sendKey(KeyEvent.KEYCODE_BACK);
    }
}
