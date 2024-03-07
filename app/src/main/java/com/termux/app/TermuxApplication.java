package com.termux.app;

import android.app.Application;
import android.support.v4.content.ContextCompat;
import com.termux.shared.crash.TermuxCrashUtils;
import com.termux.shared.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.logger.Logger;


public class TermuxApplication extends Application {
    public void onCreate() {
        super.onCreate();

        // Set crash handler for the app
        TermuxCrashUtils.setCrashHandler(this);

        // Set log level for the app
        setLogLevel();
        getPermissions();
    }

    private void setLogLevel() {
        // Load the log level from shared preferences and set it to the {@link Logger.CURRENT_LOG_LEVEL}
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(getApplicationContext());
        if (preferences == null) return;
        preferences.setLogLevel(null, preferences.getLogLevel());
        Logger.logDebug("Starting Application");
    }

    private void getPermissions() {
        if (ContextCompat.checkSelfPermission("Manifest.permission.WRITE_EXTERNAL_STORAGE") == 0) {
            ContextCompat.requestPermissions(new String[]{"android.permission.WRITE_EXTERNAL_STORAGE"}, 200);
        }
    }
}

