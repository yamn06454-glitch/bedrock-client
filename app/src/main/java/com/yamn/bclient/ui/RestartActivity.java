package com.yamn.bclient.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

/** Runs in the main process; relaunches the game after the :game process was killed. */
public class RestartActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent i = new Intent();
            i.setClassName(getPackageName(), MainActivity.GAME_ACTIVITY);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            finish();
            overridePendingTransition(0, 0);
        }, 900);
    }
}
