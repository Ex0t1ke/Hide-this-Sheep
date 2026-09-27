package com.example.myapplication;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.animation.DecelerateInterpolator;
import android.widget.ProgressBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public class SplashActivity extends AppCompatActivity {

    private ProgressBar progressBar;
    private ValueAnimator animator;
    private boolean hasNavigated = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        enterImmersiveMode();
        setContentView(R.layout.activity_splash);

        progressBar = findViewById(R.id.pb_loading);

        // Warm up preferences
        new Thread(() -> {
            new GamePrefs(getApplicationContext());
        }).start();

        startLoadingAnimation();

        // Optional tap to skip loading
        findViewById(R.id.iv_splash_bg).setOnClickListener(v -> proceedToMenu());
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

    private void startLoadingAnimation() {
        animator = ValueAnimator.ofInt(0, 100);
        animator.setDuration(1600);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            if (progressBar != null) {
                int progress = (int) animation.getAnimatedValue();
                progressBar.setProgress(progress);
                if (progress >= 100) {
                    proceedToMenu();
                }
            }
        });
        animator.start();
    }

    private synchronized void proceedToMenu() {
        if (hasNavigated) return;
        hasNavigated = true;

        if (animator != null && animator.isRunning()) {
            animator.cancel();
        }

        Intent intent = new Intent(SplashActivity.this, MenuActivity.class);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }
}
