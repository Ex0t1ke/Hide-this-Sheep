package com.example.myapplication;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public class        MenuActivity extends AppCompatActivity {

    private GamePrefs prefs;
    private SoundManager sound;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        enterImmersiveMode();
        setContentView(R.layout.activity_menu);

        prefs = new GamePrefs(this);
        sound = new SoundManager(this, prefs);

        View playBtn = findViewById(R.id.btn_play);
        View guideBtn = findViewById(R.id.btn_guide);
        View settingsBtn = findViewById(R.id.btn_settings);

        animateButton(playBtn);
        animateButton(guideBtn);
        animateButton(settingsBtn);

        playBtn.setOnClickListener(v -> {
            sound.playClick();
            showModeDialog();
        });
        guideBtn.setOnClickListener(v -> {
            sound.playClick();
            showTutorialDialog();
        });
        settingsBtn.setOnClickListener(v -> {
            sound.playClick();
            new SettingsDialogFragment().show(getSupportFragmentManager(), "settings");
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sound != null) {
            sound.release();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        enterImmersiveMode();
    }

    private void enterImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        controller.hide(WindowInsetsCompat.Type.systemBars());
    }

    // NOTE: previously built with AlertDialog.Builder(...).setView(...). Even with no title/
    // buttons set, AppCompat/MaterialComponents' AlertDialog still inflates its internal
    // topPanel/buttonPanel container views around the custom content, which showed up as
    // stray empty horizontal lines ("черточки") above and below the mode cards. A plain
    // Dialog has none of that chrome, so we use one here (same approach as the Settings dialog).
    private void showModeDialog() {
        Dialog dialog = new Dialog(this, R.style.Theme_MyApplication_Dialog);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_mode_select, null);
        dialog.setContentView(view);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setDimAmount(0.75f);
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }

        View dim = view.findViewById(R.id.dialog_dim_overlay);
        if (dim != null) dim.setOnClickListener(v -> dialog.dismiss());
        View frame = view.findViewById(R.id.dialog_frame);
        if (frame != null) frame.setOnClickListener(v -> {});

        View btnCampaign = view.findViewById(R.id.btn_mode_campaign);
        View btnEndless = view.findViewById(R.id.btn_mode_endless);

        animateButton(btnCampaign);
        animateButton(btnEndless);

        btnCampaign.setOnClickListener(v -> {
            dialog.dismiss();
            Intent intent = new Intent(MenuActivity.this, LevelSelectActivity.class);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });

        btnEndless.setOnClickListener(v -> {
            dialog.dismiss();
            Intent intent = new Intent(MenuActivity.this, MainActivity.class);
            intent.putExtra(MainActivity.EXTRA_MODE, "endless");
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });

        dialog.show();
        if (view != null) {
            view.setAlpha(0f);
            view.setScaleX(0.9f);
            view.setScaleY(0.9f);
            view.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).start();
        }
    }

    private void showTutorialDialog() {
        Dialog dialog = new Dialog(this, R.style.Theme_MyApplication_Dialog);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_tutorial, null);
        dialog.setContentView(view);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setDimAmount(0.75f);
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }

        TutorialDemoView demoView = view.findViewById(R.id.tutorial_demo_view);
        Button btnBad = view.findViewById(R.id.btn_tab_bad);
        Button btnGood = view.findViewById(R.id.btn_tab_good);
        Button btnOk = view.findViewById(R.id.btn_tutorial_ok);

        animateButton(btnBad);
        animateButton(btnGood);
        animateButton(btnOk);

        Runnable updateTabs = () -> {
            boolean isGood = demoView.isGoodMode();
            btnGood.setBackgroundResource(isGood ? R.drawable.bg_btn_3d_green : R.drawable.bg_btn_3d_wood);
            btnGood.setTextColor(isGood ? Color.WHITE : Color.parseColor("#FFE082"));
            btnBad.setBackgroundResource(!isGood ? R.drawable.bg_btn_3d_green : R.drawable.bg_btn_3d_wood);
            btnBad.setTextColor(!isGood ? Color.WHITE : Color.parseColor("#FFCDD2"));
        };

        btnBad.setOnClickListener(v -> {
            sound.playClick();
            demoView.setGoodMode(false);
            updateTabs.run();
        });

        btnGood.setOnClickListener(v -> {
            sound.playClick();
            demoView.setGoodMode(true);
            updateTabs.run();
        });

        btnOk.setOnClickListener(v -> {
            sound.playClick();
            dialog.dismiss();
        });

        updateTabs.run();
        dialog.show();
        if (view != null) {
            view.setAlpha(0f);
            view.setScaleX(0.9f);
            view.setScaleY(0.9f);
            view.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).start();
        }
    }

    private void animateButton(View button) {
        if (button == null) return;
        button.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.95f).scaleY(0.95f).translationY(4f).setDuration(60).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1.0f).scaleY(1.0f).translationY(0f).setDuration(100).start();
                    break;
            }
            return false;
        });
    }
}
