package com.example.myapplication;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Game screen — manages the board, inventory, HUD and level transitions.
 * Receives mode and level from MenuActivity via Intent extras.
 */
public class MainActivity extends AppCompatActivity
        implements VictoryDialogFragment.OnVictoryActionListener {

    public static final String EXTRA_MODE = "EXTRA_MODE";
    public static final String EXTRA_LEVEL = "EXTRA_LEVEL";

    private GameView gameView;
    private CellType currentDragItem = null;

    private TextView tvSheepCount, tvWolfCount, tvFenceCount;
    private TextView tvCardSheepCount, tvCardFenceCount, tvFenceOrientation;
    private TextView tvStatus, tvLevel;
    private FrameLayout cardSheep, cardFence;
    private Button btnStartWolves, btnReset, btnUndo;
    private View btnBack, btnRestart;

    // Score display (endless mode)
    private TextView tvScore;

    private GamePrefs prefs;
    private SoundManager sound;

    // Game mode
    private String mode = "campaign"; // "campaign" or "endless"
    private int currentLevel = 1;
    private int endlessTotalScore = 0;

    private float dragStartX = 0f, dragStartY = 0f;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingDialogRunnable;

    /** Simple undo history for placements. Supports 2-cell fences with (x2, y2). */
    private static class Placement {
        final CellType type;
        final int x, y;
        final int x2, y2;
        Placement(CellType type, int x, int y) {
            this(type, x, y, -1, -1);
        }
        Placement(CellType type, int x, int y, int x2, int y2) {
            this.type = type;
            this.x = x;
            this.y = y;
            this.x2 = x2;
            this.y2 = y2;
        }
    }
    private final Deque<Placement> history = new ArrayDeque<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        enterImmersiveMode();

        prefs = new GamePrefs(this);
        sound = new SoundManager(this, prefs);
        sound.startBgm(this);

        // Read mode from intent
        Intent intent = getIntent();
        if (intent != null) {
            mode = intent.getStringExtra(EXTRA_MODE);
            if (mode == null) mode = "campaign";
            currentLevel = intent.getIntExtra(EXTRA_LEVEL, 1);
        }

        gameView = findViewById(R.id.gameView);
        tvSheepCount = findViewById(R.id.tvSheepCount);
        tvWolfCount = findViewById(R.id.tvWolfCount);
        tvFenceCount = findViewById(R.id.tvFenceCount);
        tvCardSheepCount = findViewById(R.id.tvCardSheepCount);
        tvCardFenceCount = findViewById(R.id.tvCardFenceCount);
        tvFenceOrientation = findViewById(R.id.tvFenceOrientation);
        tvStatus = findViewById(R.id.tvStatus);
        tvLevel = findViewById(R.id.tvLevel);
        cardSheep = findViewById(R.id.cardSheep);
        cardFence = findViewById(R.id.cardFence);
        btnStartWolves = findViewById(R.id.btnStartWolves);
        btnReset = findViewById(R.id.btnReset);
        btnUndo = findViewById(R.id.btnUndo);
        btnBack = findViewById(R.id.btnBack);
        btnRestart = findViewById(R.id.btnRestart);

        // Score display — may be null if not in layout
        tvScore = findViewById(R.id.tvScore);

        // Start the game at the correct level
        loadLevel(currentLevel);

        View topContainer = findViewById(R.id.topHud);
        if (topContainer != null) {
            ViewCompat.setOnApplyWindowInsetsListener(topContainer, (v, windowInsets) -> {
                Insets insets = windowInsets.getInsets(
                        WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                int minSafeTop = (int) (36 * getResources().getDisplayMetrics().density);
                int safeTop = Math.max(insets.top, minSafeTop);
                v.setTranslationY(0);
                if (v.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                    ((ViewGroup.MarginLayoutParams) v.getLayoutParams()).topMargin = safeTop + 8;
                    v.requestLayout();
                }
                return windowInsets;
            });
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.bottomPanel), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), insets.bottom + 16);
            return windowInsets;
        });

        setupInventoryDrag();
        setupButtons();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sound != null) sound.resumeBgm();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sound != null) sound.pauseBgm();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (pendingDialogRunnable != null) {
            mainHandler.removeCallbacks(pendingDialogRunnable);
            pendingDialogRunnable = null;
        }
        if (sound != null) sound.release();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enterImmersiveMode();
    }

    private void enterImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        controller.hide(WindowInsetsCompat.Type.systemBars());
    }

    // ---------------------------------------------------------------------
    // Level management
    // ---------------------------------------------------------------------

    private void loadLevel(int level) {
        currentLevel = level;
        boolean isEndless = "endless".equals(mode);
        gameView.stopSimulation();
        GameState state = new GameState(level, isEndless);
        gameView.setGameState(state);
        history.clear();
        hideStatus();
        updateHud();
        btnStartWolves.setEnabled(true);
        btnStartWolves.setAlpha(1f);
        btnUndo.setEnabled(true);
        btnUndo.setAlpha(1f);

        if (state.status == GameStatus.LEVEL_START) {
            showLevelStartDialog(state);
        }
    }

    private void showLevelStartDialog(GameState state) {
        if (isFinishing() || isDestroyed()) return;
        LevelStartDialogFragment dialog = LevelStartDialogFragment.newInstance(
                currentLevel,
                state.availableSheep,
                state.wolfSpawns.size(),
                state.availableFences,
                state.wolfSpawns.size()
        );
        dialog.setOnLevelStartListener(() -> {
            state.status = GameStatus.PLACE_SHEEP;
        });
        dialog.show(getSupportFragmentManager(), "level_start");
    }

    // ---------------------------------------------------------------------
    // Button setup
    // ---------------------------------------------------------------------

    private void setupButtons() {
        applyPressAnimation(btnBack);
        applyPressAnimation(btnStartWolves);
        applyPressAnimation(btnReset);
        applyPressAnimation(btnUndo);

        // Pause button '||' -> show pause dialog
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> showPauseDialog());
        }

        // Quick restart -> reload current level
        if (btnRestart != null) {
            btnRestart.setOnClickListener(v -> {
                sound.playClick();
                loadLevel(currentLevel);
            });
        }

        btnReset.setOnClickListener(v -> {
            sound.playClick();
            loadLevel(currentLevel);
        });

        btnUndo.setOnClickListener(v -> {
            if (history.isEmpty()) {
                Toast.makeText(this, "Пока нет ходов для отмены", Toast.LENGTH_SHORT).show();
                return;
            }
            sound.playClick();
            Placement last = history.pop();
            GameState state = gameView.getGameState();
            state.grid[last.x][last.y] = CellType.EMPTY;
            if (last.x2 >= 0 && last.y2 >= 0 && last.x2 < state.width && last.y2 < state.height) {
                state.grid[last.x2][last.y2] = CellType.EMPTY;
            }
            if (last.type == CellType.SHEEP) {
                state.availableSheep++;
            } else if (last.type == CellType.FENCE) {
                state.availableFences++;
                state.usedFences--;
            }
            gameView.markDirty();
            gameView.invalidate();
            updateHud();
        });

        btnStartWolves.setOnClickListener(v -> {
            if (gameView.getGameState().availableSheep > 0) {
                Toast.makeText(this, "Сначала разместите всех овец на поле", Toast.LENGTH_SHORT).show();
                return;
            }

            sound.playClick();
            btnStartWolves.setEnabled(false);
            btnStartWolves.setAlpha(0.6f);
            btnUndo.setEnabled(false);
            btnUndo.setAlpha(0.6f);

            // Simulation completion listener:
            // "Стадо в безопасности!" and the win sound (or wolf catch sound) only trigger
            // after the wolf has completed its run past the sheep and exited the field!
            // If there are 2 wolves, they run sequentially one by one.
            gameView.setOnSimulationFinishedListener(wolvesWin -> {
                if (isFinishing() || isDestroyed()) return;

                if (wolvesWin) {
                    sound.playLose();
                    sound.vibrate(300);
                    showStatus("Волки добрались до овец", false);
                    gameView.shakeScreen();

                    if (pendingDialogRunnable != null) mainHandler.removeCallbacks(pendingDialogRunnable);
                    pendingDialogRunnable = () -> showVictoryDialog(false);
                    mainHandler.postDelayed(pendingDialogRunnable, 750);
                } else {
                    sound.playWin();
                    showStatus("Стадо в безопасности!", true);

                    // Calculate score
                    GameState state = gameView.getGameState();
                    int score = state.calculateScore();

                    if ("endless".equals(mode)) {
                        endlessTotalScore += score;
                        prefs.setEndlessHighScore(endlessTotalScore);
                    } else {
                        prefs.setMaxUnlockedLevel(currentLevel + 1);
                    }

                    if (pendingDialogRunnable != null) mainHandler.removeCallbacks(pendingDialogRunnable);
                    pendingDialogRunnable = () -> { showStatus("'?? ? +?????'!", true); showVictoryDialog(true); };
                    mainHandler.postDelayed(pendingDialogRunnable, 750);
                }
            });

            gameView.startSimulation(gameView.getGameState().wolfSpawns, gameView.getGameState().wolfExits);
        });
    }

    private void showVictoryDialog(boolean isWin) {
        if (isFinishing() || isDestroyed()) return;
        if (getSupportFragmentManager().isStateSaved()) return;
        GameState state = gameView.getGameState();
        int score = isWin ? state.levelScore : 0;
        if ("endless".equals(mode) && isWin) {
            score = endlessTotalScore;
        }

        if (isWin && !"endless".equals(mode)) {
            float ratio = state.initialFences > 0 ? (float) state.usedFences / state.initialFences : 0f;
            int stars = (ratio <= 0.5f) ? 3 : (ratio <= 0.75f ? 2 : 1);
            prefs.setLevelStars(currentLevel, stars);
        }

        VictoryDialogFragment dialog = VictoryDialogFragment.newInstance(
                isWin,
                currentLevel,
                state.usedFences,
                state.initialFences,
                score,
                "endless".equals(mode)
        );
        dialog.show(getSupportFragmentManager(), "victory");
    }

    private void showPauseDialog() {
        if (sound != null) sound.playClick();
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_pause, null);
        Dialog dialog = new Dialog(this, R.style.Theme_MyApplication_Dialog);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(dialogView);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            dialog.getWindow().setDimAmount(0.75f);
        }

        TextView tvLevel = dialogView.findViewById(R.id.tv_pause_level);
        if (tvLevel != null) {
            tvLevel.setText("campaign".equals(mode) ? ("Уровень " + currentLevel) : "Бесконечный режим");
        }

        Button btnResume = dialogView.findViewById(R.id.btn_pause_resume);
        if (btnResume != null) {
            btnResume.setOnClickListener(v -> {
                if (sound != null) sound.playClick();
                dialog.dismiss();
            });
        }

        Button btnRestart = dialogView.findViewById(R.id.btn_pause_restart);
        if (btnRestart != null) {
            btnRestart.setOnClickListener(v -> {
                if (sound != null) sound.playClick();
                dialog.dismiss();
                loadLevel(currentLevel);
            });
        }

        Button btnMenu = dialogView.findViewById(R.id.btn_pause_menu);
        if (btnMenu != null) {
            btnMenu.setOnClickListener(v -> {
                if (sound != null) sound.playClick();
                dialog.dismiss();
                finish();
            });
        }

        dialog.show();
    }

    // VictoryDialogFragment callbacks
    @Override
    public void onNextLevel() {
        if ("campaign".equals(mode) && currentLevel >= 30) {
            Toast.makeText(this, "Поздравляем! Все 30 уровней пройдены!", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        loadLevel(currentLevel + 1);
    }

    @Override
    public void onRetry() {
        if ("endless".equals(mode)) {
            endlessTotalScore = 0;
            loadLevel(1);
        } else {
            loadLevel(currentLevel);
        }
    }

    @Override
    public void onBackToMenu() {
        finish();
    }

    private void showStatus(String text, boolean win) {
        tvStatus.setText(text);
        tvStatus.setBackground(ContextCompat.getDrawable(this,
                win ? R.drawable.bg_btn_primary : R.drawable.bg_btn_tertiary));
        tvStatus.setAlpha(0f);
        tvStatus.setVisibility(View.VISIBLE);
        tvStatus.animate().alpha(1f).setDuration(220).start();
    }

    private void hideStatus() {
        tvStatus.setVisibility(View.GONE);
        btnUndo.setEnabled(true);
        btnUndo.setAlpha(1f);
    }

    private void applyPressAnimation(View view) {
        if (view == null) return;
        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.95f).scaleY(0.95f).translationY(3f).setDuration(70).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f).translationY(0f).setDuration(100).start();
                    break;
            }
            return false;
        });
    }

    // ---------------------------------------------------------------------
    // HUD
    // ---------------------------------------------------------------------

    private void updateHud() {
        GameState state = gameView.getGameState();
        if (state == null) return;

        // Level label
        tvLevel.setText("Уровень " + currentLevel);

        if (tvSheepCount != null) tvSheepCount.setText(String.valueOf(state.availableSheep));
        if (tvWolfCount != null) tvWolfCount.setText(String.valueOf(state.wolfSpawns.size()));
        if (tvFenceCount != null) tvFenceCount.setText(String.valueOf(state.availableFences));
        if (tvCardSheepCount != null) tvCardSheepCount.setText("x" + state.availableSheep);
        if (tvCardFenceCount != null) tvCardFenceCount.setText("x" + state.availableFences);

        TextView tvStarsDisplay = findViewById(R.id.tvStarsDisplay);
        if (tvStarsDisplay != null) {
            tvStarsDisplay.setText(prefs.getLevelStars(currentLevel) + "/3");
        }

        updateCardEnabled(cardSheep, state.availableSheep > 0);
        updateCardEnabled(cardFence, state.availableFences > 0);

        // Score display for endless mode
        if (tvScore != null) {
            if ("endless".equals(mode)) {
                tvScore.setVisibility(View.VISIBLE);
                tvScore.setText("Очки: " + endlessTotalScore);
            } else {
                tvScore.setVisibility(View.GONE);
            }
        }

        updateFenceOrientationIndicator();
    }

    private void updateFenceOrientationIndicator() {
        if (tvFenceOrientation != null && gameView != null) {
            tvFenceOrientation.setText(gameView.isFenceHorizontal() ? "↔ 2x" : "↕ 2x");
        }
    }

    private void updateCardEnabled(FrameLayout card, boolean enabled) {
        card.setBackground(ContextCompat.getDrawable(this,
                enabled ? R.drawable.bg_card_item : R.drawable.bg_card_item_disabled));
        card.setAlpha(enabled ? 1f : 0.55f);
    }

    // ---------------------------------------------------------------------
    // Inventory drag
    // ---------------------------------------------------------------------

    private void setupInventoryDrag() {
        View.OnTouchListener touchListener = (v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (v.getId() == R.id.cardSheep && gameView.getGameState().availableSheep > 0) {
                    currentDragItem = CellType.SHEEP;
                } else if (v.getId() == R.id.cardFence && gameView.getGameState().availableFences > 0) {
                    currentDragItem = CellType.FENCE;
                } else {
                    return false;
                }
                dragStartX = event.getRawX();
                dragStartY = event.getRawY();
                v.animate().scaleX(1.08f).scaleY(1.08f).setDuration(100).start();
                gameView.setDragState(currentDragItem, event.getRawX(), event.getRawY());
                return true;
            }
            return false;
        };

        cardSheep.setOnTouchListener(touchListener);
        cardFence.setOnTouchListener(touchListener);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (currentDragItem != null) {
            if (ev.getAction() == MotionEvent.ACTION_MOVE) {
                gameView.setDragState(currentDragItem, ev.getRawX(), ev.getRawY());
            } else if (ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_CANCEL) {
                float dragDist = (float) Math.hypot(ev.getRawX() - dragStartX, ev.getRawY() - dragStartY);
                cardSheep.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                cardFence.animate().scaleX(1f).scaleY(1f).setDuration(120).start();

                if (currentDragItem == CellType.FENCE && dragDist < 16 * getResources().getDisplayMetrics().density) {
                    // Quick tap on fence card -> rotate/toggle orientation
                    gameView.toggleFenceOrientation();
                    if (sound != null) sound.playClick();
                    updateFenceOrientationIndicator();
                    Toast.makeText(this, gameView.isFenceHorizontal() ? "Забор: Горизонтальный ↔" : "Забор: Вертикальный ↕", Toast.LENGTH_SHORT).show();
                    currentDragItem = null;
                    gameView.clearDragState();
                    return true;
                }

                // handleDrop places the item and returns the anchor coordinate
                Coordinate placed = gameView.handleDrop(currentDragItem, ev.getRawX(), ev.getRawY());
                if (placed != null) {
                    if (currentDragItem == CellType.SHEEP) {
                        history.push(new Placement(CellType.SHEEP, placed.x, placed.y));
                        gameView.getGameState().availableSheep--;
                        sound.playBleat();
                    } else if (currentDragItem == CellType.FENCE) {
                        Coordinate second = gameView.getLastPlacedSecondary();
                        int x2 = second != null ? second.x : -1;
                        int y2 = second != null ? second.y : -1;
                        history.push(new Placement(CellType.FENCE, placed.x, placed.y, x2, y2));
                        gameView.getGameState().availableFences--;
                        gameView.getGameState().usedFences++;
                        sound.playPlace();
                    }
                    updateHud();
                } else if (currentDragItem == CellType.SHEEP) {
                    Coordinate dropCoord = gameView.getGridCoordinates(ev.getRawX(), ev.getRawY());
                    if (dropCoord != null && dropCoord.x >= 0 && dropCoord.x < gameView.getGameState().width
                            && dropCoord.y >= 0 && dropCoord.y < gameView.getGameState().height) {
                        if (!gameView.isSafeForSheep(dropCoord.x, dropCoord.y)) {
                            Toast.makeText(this, "Овечек нужно прятать в укрытиях на пастбище", Toast.LENGTH_SHORT).show();
                        }
                    }
                } else if (currentDragItem == CellType.FENCE) {
                    Coordinate dropCoord = gameView.getGridCoordinates(ev.getRawX(), ev.getRawY());
                    if (dropCoord != null && dropCoord.x >= 0 && dropCoord.x < gameView.getGameState().width
                            && dropCoord.y >= 0 && dropCoord.y < gameView.getGameState().height) {
                        Toast.makeText(this, "Двойной забор не помещается или блокирует проход волка!", Toast.LENGTH_SHORT).show();
                    }
                }
                currentDragItem = null;
                gameView.clearDragState();
                return true; // Consume event to prevent accidental button clicks
            }
        }
        return super.dispatchTouchEvent(ev);
    }
}
