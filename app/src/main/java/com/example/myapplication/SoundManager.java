package com.example.myapplication;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

public class SoundManager {
    private static final String TAG = "SoundManager";
    
    private SoundPool soundPool;
    private final GamePrefs gamePrefs;
    private final Vibrator vibrator;
    
    private int placeSoundId = -1;
    private int clickSoundId = -1;
    private int winSoundId = -1;
    private int loseSoundId = -1;
    private int bleatSoundId = -1;

    public SoundManager(Context context, GamePrefs gamePrefs) {
        this.gamePrefs = gamePrefs;
        this.vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
                
        soundPool = new SoundPool.Builder()
                .setMaxStreams(5)
                .setAudioAttributes(audioAttributes)
                .build();
                
        loadSounds(context);
    }
    
    private void loadSounds(Context context) {
        try {
            placeSoundId = loadSoundIfAvailable(context, "place");
            clickSoundId = loadSoundIfAvailable(context, "click");
            winSoundId = loadSoundIfAvailable(context, "win");
            loseSoundId = loadSoundIfAvailable(context, "lose");
            bleatSoundId = loadSoundIfAvailable(context, "bleat");
        } catch (Exception e) {
            Log.w(TAG, "Error loading sounds", e);
        }
    }
    
    private int loadSoundIfAvailable(Context context, String soundName) {
        try {
            int resId = context.getResources().getIdentifier(soundName, "raw", context.getPackageName());
            if (resId != 0) {
                return soundPool.load(context, resId, 1);
            } else {
                Log.w(TAG, "Sound resource not found: " + soundName);
                return -1;
            }
        } catch (Exception e) {
            Log.w(TAG, "Exception while loading sound: " + soundName, e);
            return -1;
        }
    }
    
    private void playSound(int soundId) {
        if (soundId != -1 && soundPool != null) {
            float volume = gamePrefs.getSfxVolume();
            soundPool.play(soundId, volume, volume, 1, 0, 1.0f);
        }
    }
    
    public void playPlace() {
        playSound(placeSoundId);
    }
    
    public void playClick() {
        playSound(clickSoundId);
    }
    
    public void playWin() {
        playSound(winSoundId);
    }
    
    public void playLose() {
        playSound(loseSoundId);
    }
    
    public void playBleat() {
        playSound(bleatSoundId);
    }
    
    private MediaPlayer bgmPlayer;

    public void startBgm(Context context) {
        if (bgmPlayer != null) return;
        try {
            int bgmResId = context.getResources().getIdentifier("bgm", "raw", context.getPackageName());
            if (bgmResId != 0) {
                bgmPlayer = MediaPlayer.create(context.getApplicationContext(), bgmResId);
                if (bgmPlayer != null) {
                    bgmPlayer.setLooping(true);
                    updateBgmVolume();
                    bgmPlayer.start();
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error starting BGM", e);
        }
    }

    public void updateBgmVolume() {
        if (bgmPlayer != null) {
            float vol = gamePrefs.getBgmVolume();
            bgmPlayer.setVolume(vol, vol);
        }
    }

    public void pauseBgm() {
        if (bgmPlayer != null && bgmPlayer.isPlaying()) {
            bgmPlayer.pause();
        }
    }

    public void resumeBgm() {
        if (bgmPlayer != null && gamePrefs.getBgmVolume() > 0) {
            bgmPlayer.start();
        }
    }

    @SuppressWarnings("deprecation")
    public void vibrate(long ms) {
        if (vibrator != null && vibrator.hasVibrator() && gamePrefs.isVibrationEnabled()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(ms);
            }
        }
    }
    
    public void release() {
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
        if (bgmPlayer != null) {
            bgmPlayer.stop();
            bgmPlayer.release();
            bgmPlayer = null;
        }
    }
}
