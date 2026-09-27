package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public class LevelSelectActivity extends AppCompatActivity {

    public static final int TOTAL_LEVELS = 30;
    private static final int LEVELS_PER_PAGE = 15;
    private static final int TOTAL_PAGES = 2;

    private int currentPage = 0; // 0: levels 1-15, 1: levels 16-30

    private GamePrefs prefs;
    private SoundManager sound;
    private GridView gridLevels;
    private LevelAdapter adapter;
    private TextView tvTotalStars;
    private View btnPagePrev;
    private View btnPageNext;
    private View dotPage1;
    private View dotPage2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        enterImmersiveMode();
        setContentView(R.layout.activity_level_select);

        prefs = new GamePrefs(this);
        sound = new SoundManager(this, prefs);

        View topBar = findViewById(R.id.topBar);
        View btnBack = findViewById(R.id.btn_back);
        tvTotalStars = findViewById(R.id.tv_total_stars);
        gridLevels = findViewById(R.id.grid_levels);
        btnPagePrev = findViewById(R.id.btn_page_prev);
        btnPageNext = findViewById(R.id.btn_page_next);
        dotPage1 = findViewById(R.id.dot_page1);
        dotPage2 = findViewById(R.id.dot_page2);

        if (topBar != null) {
            ViewCompat.setOnApplyWindowInsetsListener(topBar, (v, insets) -> {
                int statusBarTop = insets.getInsets(WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.displayCutout()).top;
                int basePadTop = (int) (18 * getResources().getDisplayMetrics().density);
                v.setPadding(v.getPaddingLeft(), Math.max(basePadTop, statusBarTop + (int) (6 * getResources().getDisplayMetrics().density)), v.getPaddingRight(), v.getPaddingBottom());
                return insets;
            });
        }

        applyPressFeedback(btnBack);
        applyPressFeedback(btnPagePrev);
        applyPressFeedback(btnPageNext);

        btnBack.setOnClickListener(v -> {
            sound.playClick();
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });

        btnPagePrev.setOnClickListener(v -> {
            if (currentPage > 0) {
                sound.playClick();
                currentPage--;
                updatePagination();
            }
        });

        btnPageNext.setOnClickListener(v -> {
            if (currentPage < TOTAL_PAGES - 1) {
                sound.playClick();
                currentPage++;
                updatePagination();
            }
        });

        adapter = new LevelAdapter();
        gridLevels.setAdapter(adapter);

        updatePagination();
    }

    private void applyPressFeedback(View view) {
        if (view == null) return;
        view.setOnTouchListener((v, event) -> {
            if (!v.isEnabled()) return false;
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.90f).scaleY(0.90f).setDuration(60).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
                    break;
            }
            return false;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        enterImmersiveMode();
        updatePagination();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sound != null) sound.release();
    }

    private void updatePagination() {
        if (tvTotalStars != null) {
            tvTotalStars.setText(prefs.getTotalStars() + "/" + (TOTAL_LEVELS * 3));
        }

        btnPagePrev.setAlpha(currentPage == 0 ? 0.40f : 1.0f);
        btnPagePrev.setEnabled(currentPage > 0);

        btnPageNext.setAlpha(currentPage == TOTAL_PAGES - 1 ? 0.40f : 1.0f);
        btnPageNext.setEnabled(currentPage < TOTAL_PAGES - 1);

        if (dotPage1 != null) {
            dotPage1.setAlpha(currentPage == 0 ? 1.0f : 0.35f);
            dotPage1.setScaleX(currentPage == 0 ? 1.15f : 0.85f);
            dotPage1.setScaleY(currentPage == 0 ? 1.15f : 0.85f);
        }
        if (dotPage2 != null) {
            dotPage2.setAlpha(currentPage == 1 ? 1.0f : 0.35f);
            dotPage2.setScaleX(currentPage == 1 ? 1.15f : 0.85f);
            dotPage2.setScaleY(currentPage == 1 ? 1.15f : 0.85f);
        }

        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void enterImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        controller.hide(WindowInsetsCompat.Type.systemBars());
    }

    private class LevelAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return LEVELS_PER_PAGE;
        }

        @Override
        public Object getItem(int position) {
            return currentPage * LEVELS_PER_PAGE + position + 1;
        }

        @Override
        public long getItemId(int position) {
            return currentPage * LEVELS_PER_PAGE + position + 1;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = LayoutInflater.from(LevelSelectActivity.this)
                        .inflate(R.layout.item_level_card, parent, false);
            }

            int level = currentPage * LEVELS_PER_PAGE + position + 1;
            int maxUnlocked = prefs.getMaxUnlockedLevel();
            boolean isUnlocked = (level <= maxUnlocked);

            View layoutUnlocked = view.findViewById(R.id.layout_unlocked);
            View layoutLocked = view.findViewById(R.id.layout_locked);
            TextView tvNum = view.findViewById(R.id.tv_level_num);
            TextView tvLockedNum = view.findViewById(R.id.tv_locked_num);

            ImageView ivStar1 = view.findViewById(R.id.iv_star1);
            ImageView ivStar2 = view.findViewById(R.id.iv_star2);
            ImageView ivStar3 = view.findViewById(R.id.iv_star3);

            if (isUnlocked) {
                view.setBackground(ContextCompat.getDrawable(LevelSelectActivity.this, R.drawable.bg_level_card_unlocked));
                layoutUnlocked.setVisibility(View.VISIBLE);
                layoutLocked.setVisibility(View.GONE);
                tvNum.setText(String.valueOf(level));

                int stars = prefs.getLevelStars(level);
                ivStar1.setAlpha(stars >= 1 ? 1.0f : 0.25f);
                ivStar2.setAlpha(stars >= 2 ? 1.0f : 0.25f);
                ivStar3.setAlpha(stars >= 3 ? 1.0f : 0.25f);

                view.setClickable(true);
                view.setFocusable(true);

                view.setOnTouchListener((v, event) -> {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(60).start();
                            break;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
                            break;
                    }
                    return false;
                });

                view.setOnClickListener(v -> {
                    sound.playClick();
                    Intent intent = new Intent(LevelSelectActivity.this, MainActivity.class);
                    intent.putExtra(MainActivity.EXTRA_MODE, "campaign");
                    intent.putExtra(MainActivity.EXTRA_LEVEL, level);
                    startActivity(intent);
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                });
            } else {
                view.setBackground(ContextCompat.getDrawable(LevelSelectActivity.this, R.drawable.bg_level_card_locked));
                layoutUnlocked.setVisibility(View.GONE);
                layoutLocked.setVisibility(View.VISIBLE);
                tvLockedNum.setText(String.valueOf(level));

                view.setClickable(false);
                view.setFocusable(false);
                view.setOnTouchListener(null);
                view.setOnClickListener(null);
            }

            return view;
        }
    }
}
