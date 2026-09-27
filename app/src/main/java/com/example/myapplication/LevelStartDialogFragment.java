package com.example.myapplication;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

public class LevelStartDialogFragment extends DialogFragment {

    public interface OnLevelStartListener {
        void onStartGame();
    }

    private OnLevelStartListener listener;

    public static LevelStartDialogFragment newInstance(int level, int sheep, int wolves, int fences, int gates) {
        LevelStartDialogFragment fragment = new LevelStartDialogFragment();
        Bundle args = new Bundle();
        args.putInt("level", level);
        args.putInt("sheep", sheep);
        args.putInt("wolves", wolves);
        args.putInt("fences", fences);
        args.putInt("gates", gates);
        fragment.setArguments(args);
        fragment.setCancelable(false);
        return fragment;
    }

    public void setOnLevelStartListener(OnLevelStartListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_level_start, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        if (args == null) return;

        int level = args.getInt("level", 1);
        int sheep = args.getInt("sheep", 1);
        int wolves = args.getInt("wolves", 1);
        int fences = args.getInt("fences", 8);
        int gates = args.getInt("gates", 1);

        TextView tvTitle = view.findViewById(R.id.tv_start_level_title);
        TextView tvSheep = view.findViewById(R.id.tv_start_sheep);
        TextView tvWolves = view.findViewById(R.id.tv_start_wolves);
        TextView tvFences = view.findViewById(R.id.tv_start_fences);
        TextView tvGates = view.findViewById(R.id.tv_start_gates);
        Button btnStart = view.findViewById(R.id.btn_start_level);

        tvTitle.setText("Уровень " + level);
        tvSheep.setText("Овец: " + sheep);
        tvWolves.setText("Волков: " + wolves);
        tvFences.setText("Доп. заборы: " + fences);
        tvGates.setText("Входы: " + gates);

        btnStart.setOnClickListener(v -> {
            if (listener != null) {
                listener.onStartGame();
            }
            dismiss();
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }
}
