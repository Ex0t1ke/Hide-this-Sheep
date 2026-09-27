package com.example.myapplication;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.DialogFragment;

public class SettingsDialogFragment extends DialogFragment {

    private GamePrefs prefs;
    private SoundManager sound;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setDimAmount(0.75f);
        }
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.setAlpha(0f);
        view.setScaleX(0.9f);
        view.setScaleY(0.9f);
        view.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).start();

        prefs = new GamePrefs(requireContext());
        sound = new SoundManager(requireContext(), prefs);

        View dimOverlay = view.findViewById(R.id.dialog_dim_overlay);
        if (dimOverlay != null) {
            dimOverlay.setOnClickListener(v -> dismiss());
        }
        View dialogFrame = view.findViewById(R.id.dialog_frame);
        if (dialogFrame != null) {
            dialogFrame.setOnClickListener(v -> {}); // prevent outside-click from passing through
        }

        View btnClose = view.findViewById(R.id.btn_close);
        SeekBar sbBgm = view.findViewById(R.id.sb_bgm);
        SeekBar sbSfx = view.findViewById(R.id.sb_sfx);
        SwitchCompat switchVibration = view.findViewById(R.id.switch_vibration);
        View btnReset = view.findViewById(R.id.btn_reset_progress);

        sbBgm.setMax(100);
        sbSfx.setMax(100);
        sbBgm.setProgress((int) (prefs.getBgmVolume() * 100));
        sbSfx.setProgress((int) (prefs.getSfxVolume() * 100));
        switchVibration.setChecked(prefs.isVibrationEnabled());

        btnClose.setOnClickListener(v -> { sound.playClick(); dismiss(); });

        sbBgm.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) prefs.setBgmVolume(progress / 100f);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        sbSfx.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) prefs.setSfxVolume(progress / 100f);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        switchVibration.setOnCheckedChangeListener((buttonView, isChecked) -> prefs.setVibrationEnabled(isChecked));

        // NOTE: previously this used a stock AlertDialog.Builder(...).setTitle()/.setMessage(),
        // which — combined with Theme_MyApplication_Dialog's transparent windowBackground —
        // rendered with NO visible panel at all ("плашки не видно" bug). We now show a themed
        // parchment confirm dialog that matches the rest of the app's UI.
        btnReset.setOnClickListener(v -> {
            sound.playClick();
            showResetConfirmDialog();
        });
    }

    private void showResetConfirmDialog() {
        Dialog confirmDialog = new Dialog(requireContext(), R.style.Theme_MyApplication_Dialog);
        confirmDialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        View confirmView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_confirm, null);
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
            dismiss();
        });

        confirmDialog.show();
        if (confirmView != null) {
            confirmView.setAlpha(0f);
            confirmView.setScaleX(0.9f);
            confirmView.setScaleY(0.9f);
            confirmView.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).start();
        }
    }
}
