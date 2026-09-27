package com.example.myapplication;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

public class VictoryDialogFragment extends DialogFragment {

    public interface OnVictoryActionListener {
        void onNextLevel();
        void onRetry();
        void onBackToMenu();
    }

    private OnVictoryActionListener listener;

    public static VictoryDialogFragment newInstance(boolean isWin, int level, int fencesUsed, int fencesAvailable, int score, boolean isEndless) {
        VictoryDialogFragment fragment = new VictoryDialogFragment();
        Bundle args = new Bundle();
        args.putBoolean("isWin", isWin);
        args.putInt("level", level);
        args.putInt("fencesUsed", fencesUsed);
        args.putInt("fencesAvailable", fencesAvailable);
        args.putInt("score", score);
        args.putBoolean("isEndless", isEndless);
        fragment.setArguments(args);
        fragment.setCancelable(false);
        return fragment;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof OnVictoryActionListener) {
            listener = (OnVictoryActionListener) context;
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.setOnKeyListener((dialogInterface, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_SPACE) {
                    if (getArguments() != null && getArguments().getBoolean("isWin")) {
                        if (listener != null) listener.onNextLevel();
                        dismiss();
                        return true;
                    }
                }
            }
            return false;
        });
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_victory, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.setScaleX(0.85f);
        view.setScaleY(0.85f);
        view.setAlpha(0f);
        view.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .alpha(1.0f)
            .setDuration(350)
            .setInterpolator(new OvershootInterpolator(1.2f))
            .start();

        Bundle args = getArguments();
        if (args == null) return;

        boolean isWin = args.getBoolean("isWin");
        int fencesUsed = args.getInt("fencesUsed");
        int fencesAvailable = args.getInt("fencesAvailable");
        int score = args.getInt("score");
        boolean isEndless = args.getBoolean("isEndless");

        TextView tvTitle = view.findViewById(R.id.tv_victory_title);
        TextView tvStats = view.findViewById(R.id.tv_victory_stats);
        TextView tvStars = view.findViewById(R.id.tv_victory_stars);
        View containerStars = view.findViewById(R.id.container_victory_stars);
        View ivStar1 = view.findViewById(R.id.iv_star1);
        View ivStar2 = view.findViewById(R.id.iv_star2);
        View ivStar3 = view.findViewById(R.id.iv_star3);

        Button btnNext = view.findViewById(R.id.btn_victory_next);
        Button btnRetry = view.findViewById(R.id.btn_victory_retry);
        Button btnMenu = view.findViewById(R.id.btn_victory_menu);

        if (isWin) {
            tvTitle.setText(isEndless ? "Защита выдержала!" : "Уровень пройден!");
            btnNext.setVisibility(View.VISIBLE);
            btnNext.setText("Следующий уровень");

            float ratio = fencesAvailable > 0 ? (float) fencesUsed / fencesAvailable : 0f;
            int starCount = (ratio <= 0.5f) ? 3 : ((ratio <= 0.75f) ? 2 : 1);
            String rating;
            if (starCount == 3) {
                rating = "Безупречная защита!";
            } else if (starCount == 2) {
                rating = "Надёжная защита!";
            } else {
                rating = "Защита выдержала!";
            }
            tvStars.setText(rating);

            if (containerStars != null) {
                containerStars.setVisibility(View.VISIBLE);
                animateStar(ivStar1, starCount >= 1, 100);
                animateStar(ivStar2, starCount >= 2, 280);
                animateStar(ivStar3, starCount >= 3, 460);
            }

            if (isEndless) {
                tvStats.setText("Использовано заборов: " + fencesUsed + " из " + fencesAvailable
                        + "\nОбщий счёт: " + score + " очков");
            } else {
                tvStats.setText("Использовано заборов: " + fencesUsed + " из " + fencesAvailable
                        + "\nПолучено очков: +" + score);
            }
        } else {
            tvTitle.setText("Защита прорвана");
            btnNext.setVisibility(View.GONE);
            if (containerStars != null) containerStars.setVisibility(View.GONE);
            tvStars.setText("Волки нашли проход к стаду");
            tvStats.setText("Попробуйте изменить расстановку заборов и закрыть уязвимые места.");
        }

        btnNext.setOnClickListener(v -> {
            if (listener != null) listener.onNextLevel();
            dismiss();
        });

        btnRetry.setOnClickListener(v -> {
            if (listener != null) listener.onRetry();
            dismiss();
        });

        btnMenu.setOnClickListener(v -> {
            if (listener != null) listener.onBackToMenu();
            dismiss();
        });
    }

    private void animateStar(View starView, boolean active, long delayMs) {
        if (starView == null) return;
        if (active) {
            starView.setAlpha(0f);
            starView.setScaleX(0.2f);
            starView.setScaleY(0.2f);
            starView.animate()
                    .alpha(1.0f)
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setStartDelay(delayMs)
                    .setDuration(300)
                    .setInterpolator(new OvershootInterpolator(1.8f))
                    .start();
        } else {
            starView.setAlpha(0.25f);
            starView.setScaleX(0.85f);
            starView.setScaleY(0.85f);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }
}
