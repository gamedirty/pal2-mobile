package com.winlator.pal2;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.winlator.MainActivity;
import com.winlator.R;
import com.winlator.XServerDisplayActivity;
import com.winlator.container.Container;
import com.winlator.core.AppUtils;
import com.winlator.core.LocaleHelper;
import com.winlator.xenvironment.RootFSInstaller;

import java.io.File;
import java.util.Locale;

public class Pal2LauncherActivity extends AppCompatActivity {
    private TextView tvSystemStatus;
    private TextView tvGameDataStatus;
    private TextView tvContainerStatus;
    private Button bStartGame;
    private Button bReimportGame;
    private Container container;
    private boolean settingUp = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppUtils.setActivityTheme(this);
        setContentView(R.layout.activity_pal2_launcher);

        tvSystemStatus = findViewById(R.id.TVSystemStatus);
        tvGameDataStatus = findViewById(R.id.TVGameDataStatus);
        tvContainerStatus = findViewById(R.id.TVContainerStatus);
        bStartGame = findViewById(R.id.BStartGame);
        bReimportGame = findViewById(R.id.BReimportGame);
        Button bAdvanced = findViewById(R.id.BAdvanced);

        applyDefaultPreferences();

        bStartGame.setOnClickListener((view) -> startGame());
        bReimportGame.setOnClickListener((view) -> confirmReinstall());
        bAdvanced.setOnClickListener((view) -> startActivity(new Intent(this, MainActivity.class)));

        bReimportGame.setVisibility(View.GONE);
        runSetup();
    }

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(LocaleHelper.setSystemLocale(newBase));
    }

    private void applyDefaultPreferences() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = preferences.edit();
        // Tap anywhere = move the cursor there and click, the natural scheme for a point-and-click RPG.
        if (!preferences.contains("move_cursor_to_touchpoint")) editor.putBoolean("move_cursor_to_touchpoint", true);
        if (!preferences.contains("cursor_speed")) editor.putFloat("cursor_speed", 1.0f);
        editor.apply();
    }

    private void runSetup() {
        if (settingUp) return;
        settingUp = true;
        setStatus(tvSystemStatus, getString(R.string.pal2_status_installing));
        RootFSInstaller.installIfNeeded(this, this::afterRootFS);
    }

    private void afterRootFS() {
        afterRootFS(false);
    }

    private void afterRootFS(boolean forceReinstall) {
        setStatus(tvSystemStatus, getString(R.string.pal2_status_ok));
        GameSetup.ensureRootfsLocale(this);

        if (!forceReinstall && GameInstaller.isInstalled(this)) {
            setStatus(tvGameDataStatus, getString(R.string.pal2_status_ok));
            afterGameData();
            return;
        }

        setStatus(tvGameDataStatus, getString(R.string.pal2_status_copying));
        GameInstaller.installAsync(this, new GameInstaller.ProgressCallback() {
            @Override
            public void onProgress(final int percent, final long extractedBytes, final long totalBytes) {
                runOnUiThread(() -> setStatus(tvGameDataStatus, getString(R.string.pal2_status_copying)+" "+percent+"% ("+formatMB(extractedBytes)+"/"+formatMB(totalBytes)+")"));
            }
        }, (success) -> runOnUiThread(() -> {
            if (success) {
                setStatus(tvGameDataStatus, getString(R.string.pal2_status_ok));
                afterGameData();
            }
            else {
                settingUp = false;
                setStatus(tvGameDataStatus, getString(R.string.pal2_status_copy_failed));
            }
        }));
    }

    private void afterGameData() {
        GameSetup.ensureInputControlsProfile(this);
        setStatus(tvContainerStatus, getString(R.string.pal2_status_creating));
        GameSetup.ensureContainerAsync(this, (result) -> {
            settingUp = false;
            container = result;
            if (container != null) {
                final Container c = container;
                java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> GameSetup.ensureCjkFonts(this, c));
                setStatus(tvContainerStatus, getString(R.string.pal2_status_ok));
                bStartGame.setEnabled(true);
                bReimportGame.setVisibility(View.VISIBLE);
            }
            else setStatus(tvContainerStatus, getString(R.string.pal2_status_container_failed));
        });
    }

    private void startGame() {
        if (container == null) return;
        Intent intent = new Intent(this, XServerDisplayActivity.class);
        intent.putExtra("container_id", container.id);
        intent.putExtra("exec_path", new File(GameInstaller.getGameDir(this), GameInstaller.GAME_EXE).getPath());
        intent.putExtra("controls_profile_id", GameSetup.PROFILE_ID);
        intent.putExtra("force_fullscreen", true);
        startActivity(intent);
    }

    private void confirmReinstall() {
        new AlertDialog.Builder(this)
            .setTitle(R.string.pal2_reimport_game)
            .setMessage(R.string.pal2_reimport_confirm)
            .setPositiveButton(R.string.pal2_reimport_game, (dialog, which) -> {
                bStartGame.setEnabled(false);
                afterRootFS(true);
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void setStatus(final TextView view, final String text) {
        runOnUiThread(() -> view.setText(text));
    }

    private static String formatMB(long bytes) {
        return String.format(Locale.US, "%.0fMB", bytes / 1024.0 / 1024.0);
    }
}
