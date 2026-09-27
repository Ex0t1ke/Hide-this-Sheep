package com.example.myapplication;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public class SettingsActivity extends AppCompatActivity {

    private GamePrefs prefs;
    private SoundManager sound;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        enterImmersiveMode();
        setContentView(R.layout.activity_settings);

        prefs = new GamePrefs(this);
        sound = new SoundManager(this, prefs);

        View btnClose = findViewById(R.id.btn_close);
        SeekBar sbBgm = findViewById(R.id.sb_bgm);
        SeekBar sbSfx = findViewById(R.id.sb_sfx);
        SwitchCompat switchVibration = findViewById(R.id.switch_vibration);
        View btnReset = findViewById(R.id.btn_reset_progress);

        // Load current values
        sbBgm.setMax(100);
        sbSfx.setMax(100);
        sbBgm.setProgress((int) (prefs.getBgmVolume() * 100));
        sbSfx.setProgress((int) (prefs.getSfxVolume() * 100));
        switchVibration.setChecked(prefs.isVibrationEnabled());

        btnClose.setOnClickListener(v -> {
            sound.playClick();
            finish();
        });

        sbBgm.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) prefs.setBgmVolume(progress / 100f);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        sbSfx.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) prefs.setSfxVolume(progress / 100f);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        switchVibration.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.setVibrationEnabled(isChecked);
        });

        btnReset.setOnClickListener(v -> {
            sound.playClick();
            showResetConfirmDialog();
        });
    }

    // Previously this used a stock AlertDialog.Builder(...).setTitle()/.setMessage(), which —
    // combined with Theme_MyApplication_Dialog's transparent windowBackground — rendered with
    // NO visible panel at all ("плашки не видно" bug). Now uses a themed parchment dialog.
    private void showResetConfirmDialog() {
        Dialog confirmDialog = new Dialog(this, R.style.Theme_MyApplication_Dialog);
        confirmDialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        View confirmView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm, null);
        confirmDialog.setContentView(confirmView);
        if (confirmDialog.getWindow() != null) {
            confirmDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            confirmDialog.getWindow().setDimAmount(0.75f);
            confirmDialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }

        View dim = confirmView.findViewById(R.id.dialog_dim_overlay);
        if (dim != null) dim.setOnClickListener(v -> confirmDialog.dismiss());
        View frame = confirmView.findViewById(R.id.dialog_frame);
        if (frame != null) frame.setOnClickListener(v -> {});

        confirmView.findViewById(R.id.btn_confirm_cancel).setOnClickListener(v -> {
            sound.playClick();
            confirmDialog.dismiss();
        });
        confirmView.findViewById(R.id.btn_confirm_ok).setOnClickListener(v -> {
            sound.playClick();
            prefs.resetProgress();
            confirmDialog.dismiss();
            finish();
        });

        confirmDialog.show();
    }

    private void enterImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        controller.hide(WindowInsetsCompat.Type.systemBars());
    }

    @Override
    protected void onResume() {
        super.onResume();
        enterImmersiveMode();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sound != null) sound.release();
    }
}
