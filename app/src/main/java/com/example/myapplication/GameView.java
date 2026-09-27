package com.example.myapplication;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathEffect;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Renders the sheep-and-wolves puzzle board.
 *
 * Game logic (grid contents, pathfinding, level structure) lives entirely in
 * {@link GameState} / {@link Pathfinder} and is NOT touched here - this class
 * is purely presentational.
 *
 * Rendering strategy:
 *  - The terrain (grass) and anything static (fixed/placed fences, wolf gates)
 *    is baked once into an offscreen {@link #staticLayer} bitmap and only
 *    rebuilt when the view is resized or the grid changes (see markDirty()).
 *  - Sheep, wolves and the drag "ghost" are animated, so they are redrawn
 *    every frame on top of the cached static layer.
 */
public class GameView extends View {

    private Paint paint;
    private Paint gradientPaint;
    private GameState gameState;

    // Grid metrics
    private float cellSize;
    private float offsetX, offsetY;

    // Animation clock
    private long startTime;

    // Wolves sequential animation
    public static class WolfSim {
        public Coordinate spawn;
        public Coordinate exit;
        public List<Coordinate> path = new ArrayList<>();
        public boolean reachesSheep = false;
        public long startMs = 0;
        public long durationMs = 0;
    }
    private boolean isSimulating = false;
    private boolean currentWolvesWin = true;
    private final List<WolfSim> wolfSims = new ArrayList<>();
    private boolean simulationFinishedNotified = false;
    private long simulationStartTime;
    private static final long WOLF_STEP_MS = 320; // ms per grid step

    public interface OnSimulationFinishedListener {
        void onSimulationFinished(boolean wolvesWin);
    }
    private OnSimulationFinishedListener simulationFinishedListener;

    public void setOnSimulationFinishedListener(OnSimulationFinishedListener listener) {
        this.simulationFinishedListener = listener;
    }

    // Deterministic decoration noise
    private final Random random = new Random(12345);

    // --- Static layer cache (grass + fences + gates) ---
    private Bitmap staticLayer;
    private Canvas staticLayerCanvas;
    private boolean staticLayerDirty = true;
    private int cachedW = -1, cachedH = -1;

    // --- Recently placed items get a little "pop" animation ---
    private static class PlacedAnim {
        int x, y;
        long time;
        CellType type;
        PlacedAnim(int x, int y, CellType type, long time) {
            this.x = x; this.y = y; this.type = type; this.time = time;
        }
    }
    private final List<PlacedAnim> recentPlacements = new ArrayList<>();
    private static final long POP_DURATION_MS = 320;

    // Reusable objects to avoid per-frame allocations
    private final RectF rectF = new RectF();
    private final Path path = new Path();

    public GameView(Context context) {
        super(context);
        init();
    }

    public GameView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public GameView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private Paint dashPaint;

    private void init() {
        paint = new Paint();
        paint.setAntiAlias(true);
        gradientPaint = new Paint();
        gradientPaint.setAntiAlias(true);
        dashPaint = new Paint();
        dashPaint.setAntiAlias(true);
        dashPaint.setStyle(Paint.Style.STROKE);
        dashPaint.setColor(Color.argb(230, 41, 182, 246));
        gameState = new GameState();
        startTime = System.currentTimeMillis();
    }

    public void setGameState(GameState state) {
        this.gameState = state;
        recentPlacements.clear();
        markDirty();
        invalidate();
    }

    public GameState getGameState() {
        return gameState;
    }

    /** Call whenever the grid contents change (fence placed) so the cached background is rebuilt. */
    public void markDirty() {
        staticLayerDirty = true;
    }

    public void startSimulation(List<Coordinate> spawns, List<Coordinate> exits, boolean unused) {
        startSimulation(spawns, exits);
    }

    public void startSimulation(List<Coordinate> spawns, List<Coordinate> exits) {
        this.isSimulating = true;
        this.simulationFinishedNotified = false;
        this.simulationStartTime = System.currentTimeMillis();
        this.wolfSims.clear();

        if (spawns != null && gameState != null) {
            for (int i = 0; i < spawns.size(); i++) {
                Coordinate spawn = spawns.get(i);
                Coordinate landing = gameState.getWolfLandingCoord(spawn);
                Coordinate exit = (exits != null && i < exits.size()) ? exits.get(i) : null;
                Coordinate exitLanding = (exit != null) ? gameState.getWolfLandingCoord(exit) : exit;

                List<Coordinate> pathToSheep = Pathfinder.findPathToSheep(gameState, landing);
                if (!pathToSheep.isEmpty()) {
                    // This wolf can reach sheep!
                    WolfSim sim = new WolfSim();
                    sim.spawn = spawn;
                    sim.exit = exit;
                    sim.reachesSheep = true;
                    sim.path.add(spawn);
                    sim.path.addAll(pathToSheep);
                    wolfSims.add(sim);
                    // Once a wolf reaches sheep, the herd is breached; subsequent wolves do not run.
                    break;
                } else {
                    // This wolf cannot reach sheep: runs across field toward exit gate
                    List<Coordinate> pathToExit = Pathfinder.findPathToExit(gameState, landing, exitLanding);
                    WolfSim sim = new WolfSim();
                    sim.spawn = spawn;
                    sim.exit = exit;
                    sim.reachesSheep = false;
                    sim.path.add(spawn);
                    sim.path.addAll(pathToExit);
                    if (exit != null && !pathToExit.isEmpty()) {
                        Coordinate last = pathToExit.get(pathToExit.size() - 1);
                        if (Math.abs(last.x - exit.x) <= 1 && Math.abs(last.y - exit.y) <= 1) {
                            sim.path.add(exit);
                        }
                    }
                    wolfSims.add(sim);
                }
            }
        }

        this.currentWolvesWin = !wolfSims.isEmpty() && wolfSims.get(wolfSims.size() - 1).reachesSheep;

        // Calculate sequential start times:
        // Wolf 1 runs, exits through the gate and disappears.
        // Then, after a distinct gap, Wolf 2 enters and runs.
        long currentStart = 0;
        for (int i = 0; i < wolfSims.size(); i++) {
            WolfSim sim = wolfSims.get(i);
            sim.startMs = currentStart;
            int steps = Math.max(1, sim.path.size() - 1);
            sim.durationMs = steps * WOLF_STEP_MS;
            long runAndExitTime = sim.durationMs + (sim.reachesSheep ? 0 : WOLF_STEP_MS);
            currentStart = sim.startMs + runAndExitTime + 450;
        }

        invalidate();
    }

    public void startSimulation(List<Coordinate> spawns) {
        startSimulation(spawns, null);
    }

    public long getSimulationDurationMs() {
        if (wolfSims.isEmpty()) return 0;
        WolfSim last = wolfSims.get(wolfSims.size() - 1);
        if (last.reachesSheep) {
            return last.startMs + last.durationMs;
        } else {
            return last.startMs + last.durationMs + WOLF_STEP_MS;
        }
    }

    public void stopSimulation() {
        this.isSimulating = false;
        this.simulationFinishedNotified = false;
        this.wolfSims.clear();
        invalidate();
    }

    /** Returns true if the simulation animation has finished (all wolves reached their targets). */
    public boolean isSimulationFinished() {
        if (!isSimulating || wolfSims.isEmpty()) return !isSimulating;
        long elapsed = System.currentTimeMillis() - simulationStartTime;
        return elapsed >= getSimulationDurationMs();
    }

    // ---------------------------------------------------------------------
    // Drag and drop
    // ---------------------------------------------------------------------

    private CellType draggedType = null;
    private float dragX = -1f;
    private float dragY = -1f;

    // 2-cell fence orientation: true = Horizontal (x, y) & (x+1, y), false = Vertical (x, y) & (x, y+1)
    private boolean fenceHorizontal = true;
    private Coordinate lastPlacedSecondary = null;

    public boolean isFenceHorizontal() {
        return fenceHorizontal;
    }

    public void setFenceHorizontal(boolean horizontal) {
        this.fenceHorizontal = horizontal;
        invalidate();
    }

    public void toggleFenceOrientation() {
        setFenceHorizontal(!fenceHorizontal);
    }

    public Coordinate getLastPlacedSecondary() {
        return lastPlacedSecondary;
    }

    public void setDragState(CellType type, float x, float y) {
        this.draggedType = type;
        this.dragX = x;
        this.dragY = y;
        invalidate();
    }

    public void clearDragState() {
        this.draggedType = null;
        invalidate();
    }

    /**
     * Determines whether a coordinate lies within the safe inner pasture.
     * Sheep can only be placed on the inner pasture [2..width-3] x [2..height-3]
     * on an EMPTY cell (cannot be placed on rocks, walls, or outer borders).
     */
    public boolean isSafeForSheep(int gridX, int gridY) {
        if (gameState == null) return false;
        if (gridX < 2 || gridX > gameState.width - 3 || gridY < 2 || gridY > gameState.height - 3) {
            return false;
        }
        return gameState.grid[gridX][gridY] == CellType.EMPTY;
    }

    public boolean isInnerPasture(int gridX, int gridY) {
        return isSafeForSheep(gridX, gridY);
    }

    /**
     * Anti-abuse dead-zone rule: Fences cannot be placed on the outer boundary
     * wall OR within the wolf entry/exit clearings and guaranteed 1-cell corridors.
     * This ensures wolves can never be boxed in right at the gate with a double fence!
     */
    public boolean isTileValidForFence(int gridX, int gridY) {
        if (gameState == null) return false;
        // Cannot place on outer perimeter boundary
        if (gridX <= 0 || gridX >= gameState.width - 1 || gridY <= 0 || gridY >= gameState.height - 1) {
            return false;
        }
        // Must be empty
        if (gameState.grid[gridX][gridY] != CellType.EMPTY) {
            return false;
        }
        // Buffer around wolf spawn points (including 1-tile entrance corridor)
        if (gameState.wolfSpawns != null) {
            for (Coordinate spawn : gameState.wolfSpawns) {
                if (Math.abs(gridX - spawn.x) <= 1 && Math.abs(gridY - spawn.y) <= 1) {
                    return false;
                }
                // Protected 1-cell corridor into the field so wolf cannot be barricaded
                if (spawn.y == 0 && gridX == spawn.x && gridY == 2) return false;
                if (spawn.y == gameState.height - 1 && gridX == spawn.x && gridY == gameState.height - 3) return false;
                if (spawn.x == 0 && gridX == 2 && gridY == spawn.y) return false;
                if (spawn.x == gameState.width - 1 && gridX == gameState.width - 3 && gridY == spawn.y) return false;
            }
        }
        // Buffer around wolf exit points (including 1-tile exit corridor)
        if (gameState.wolfExits != null) {
            for (Coordinate exit : gameState.wolfExits) {
                if (Math.abs(gridX - exit.x) <= 1 && Math.abs(gridY - exit.y) <= 1) {
                    return false;
                }
                // Protected 1-cell corridor out of the field so wolf can always exit
                if (exit.y == 0 && gridX == exit.x && gridY == 2) return false;
                if (exit.y == gameState.height - 1 && gridX == exit.x && gridY == gameState.height - 3) return false;
                if (exit.x == 0 && gridX == 2 && gridY == exit.y) return false;
                if (exit.x == gameState.width - 1 && gridX == gameState.width - 3 && gridY == exit.y) return false;
            }
        }
        return true;
    }

    /**
     * Maps raw screen touch coordinates to grid coordinates.
     */
    public Coordinate getGridCoordinates(float rawX, float rawY) {
        if (cellSize <= 0) return null;
        int[] location = new int[2];
        getLocationOnScreen(location);
        float localX = rawX - location[0];
        float localY = rawY - location[1];
        int gridX = (int) ((localX - offsetX) / cellSize);
        int gridY = (int) ((localY - offsetY) / cellSize);
        return new Coordinate(gridX, gridY);
    }

    /**
     * Attempts to place the dragged item on the grid.
     * - Sheep occupies 1 safe cell on the inner pasture.
     * - Fence occupies 2 contiguous cells (1x2 horizontal or 2x1 vertical).
     * @return The anchor grid coordinate where the item was placed, or null if invalid.
     */
    public Coordinate handleDrop(CellType type, float rawX, float rawY) {
        if (type == null) return null;
        if (cellSize <= 0) return null;

        Coordinate gridCoord = getGridCoordinates(rawX, rawY);
        if (gridCoord == null) return null;
        int gridX = gridCoord.x;
        int gridY = gridCoord.y;

        if (gridX >= 0 && gridX < gameState.width && gridY >= 0 && gridY < gameState.height) {
            if (type == CellType.SHEEP) {
                if (!isInnerPasture(gridX, gridY)) {
                    return null;
                }
                if (gameState.grid[gridX][gridY] == CellType.EMPTY) {
                    gameState.grid[gridX][gridY] = CellType.SHEEP;
                    registerPlacement(gridX, gridY, CellType.SHEEP);
                    lastPlacedSecondary = null;
                    markDirty();
                    invalidate();
                    return new Coordinate(gridX, gridY);
                }
            } else if (type == CellType.FENCE) {
                int x1 = gridX;
                int y1 = gridY;
                if (fenceHorizontal && x1 >= gameState.width - 2) x1 = gameState.width - 3;
                if (!fenceHorizontal && y1 >= gameState.height - 2) y1 = gameState.height - 3;
                int x2 = fenceHorizontal ? x1 + 1 : x1;
                int y2 = fenceHorizontal ? y1 : y1 + 1;

                if (isTileValidForFence(x1, y1) && isTileValidForFence(x2, y2)) {
                    gameState.grid[x1][y1] = CellType.FENCE;
                    gameState.grid[x2][y2] = CellType.FENCE;
                    registerPlacement(x1, y1, CellType.FENCE);
                    registerPlacement(x2, y2, CellType.FENCE);
                    lastPlacedSecondary = new Coordinate(x2, y2);
                    markDirty();
                    invalidate();
                    return new Coordinate(x1, y1);
                }
            }
        }
        return null;
    }

    private void registerPlacement(int x, int y, CellType type) {
        recentPlacements.add(new PlacedAnim(x, y, type, System.currentTimeMillis()));
    }

    /** Returns the pop-animation scale (1 -&gt; overshoot -&gt; settle) for a cell, or 1f if none active. */
    private float popScaleFor(int x, int y) {
        long now = System.currentTimeMillis();
        for (int i = recentPlacements.size() - 1; i >= 0; i--) {
            PlacedAnim p = recentPlacements.get(i);
            long elapsed = now - p.time;
            if (elapsed > POP_DURATION_MS) {
                recentPlacements.remove(i);
                continue;
            }
            if (p.x == x && p.y == y) {
                float t = elapsed / (float) POP_DURATION_MS;
                // simple overshoot ease: 1 -> 1.35 -> 1
                float overshoot = (float) Math.sin(t * Math.PI) * 0.35f;
                return 1f + overshoot * (1f - t);
            }
        }
        return 1f;
    }

    // ---------------------------------------------------------------------
    // Screen shake (for lose feedback)
    // ---------------------------------------------------------------------

    /** Plays a short horizontal shake animation on this view. */
    public void shakeScreen() {
        ObjectAnimator shake = ObjectAnimator.ofFloat(this, "translationX",
                0, 12, -12, 10, -8, 5, -3, 0);
        shake.setDuration(400);
        shake.start();
    }

    // ---------------------------------------------------------------------
    // Main draw loop
    // ---------------------------------------------------------------------

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (gameState == null) return;

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float maxCellWidth = (float) w / gameState.width;
        float maxCellHeight = (float) h / gameState.height;
        cellSize = Math.min(maxCellWidth, maxCellHeight) * 0.92f;

        offsetX = (w - (cellSize * gameState.width)) / 2f;
        // Shift grid upward by 3% of height to avoid bottom panel overlap
        offsetY = (h - (cellSize * gameState.height)) / 2f - (h * 0.03f);

        ensureStaticLayer(w, h);
        canvas.drawBitmap(staticLayer, 0, 0, null);

        drawWolfGateBadges(canvas);

        drawLiveSheep(canvas);

        if (isSimulating) {
            drawAnimatedWolves(canvas);
        }

        drawSnapPulses(canvas);
        drawGhost(canvas);

        // Only keep animating when there's something to animate (save battery)
        boolean needsAnimation = isSimulating
                || draggedType != null
                || !recentPlacements.isEmpty();
        if (needsAnimation) {
            postInvalidateOnAnimation();
        }
    }

    private void ensureStaticLayer(int w, int h) {
        if (staticLayer == null || cachedW != w || cachedH != h || staticLayerDirty) {
            if (staticLayer == null || cachedW != w || cachedH != h) {
                // Recycle old bitmap to prevent GPU memory leak
                if (staticLayer != null) {
                    staticLayer.recycle();
                }
                staticLayer = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                staticLayerCanvas = new Canvas(staticLayer);
                cachedW = w;
                cachedH = h;
            } else {
                // Same size but dirty — clear and redraw
                staticLayer.eraseColor(Color.TRANSPARENT);
            }
            buildStaticLayer(staticLayerCanvas, w, h);
            staticLayerDirty = false;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (staticLayer != null) {
            staticLayer.recycle();
            staticLayer = null;
            staticLayerCanvas = null;
        }
    }

    public boolean isWolfGate(int x, int y) {
        if (gameState == null) return false;
        if (gameState.wolfSpawns != null) {
            for (Coordinate c : gameState.wolfSpawns) {
                if (c.x == x && c.y == y) return true;
            }
        }
        if (gameState.wolfExits != null) {
            for (Coordinate c : gameState.wolfExits) {
                if (c.x == x && c.y == y) return true;
            }
        }
        return gameState.grid != null && gameState.grid[x][y] == CellType.WOLF_SPAWN;
    }

    private void buildStaticLayer(Canvas canvas, int w, int h) {
        drawGrassBackground(canvas, w, h);
        drawSubtleGridDots(canvas);

        for (int x = 0; x < gameState.width; x++) {
            for (int y = 0; y < gameState.height; y++) {
                float cx = offsetX + x * cellSize;
                float cy = offsetY + y * cellSize;
                if (isWolfGate(x, y)) {
                    drawWolfGate(canvas, x, y, cx, cy);
                } else if (gameState.grid[x][y] == CellType.FENCE) {
                    drawFence(canvas, x, y, cx, cy, 1f);
                }
            }
        }
    }

    private void drawLiveSheep(Canvas canvas) {
        for (int x = 0; x < gameState.width; x++) {
            for (int y = 0; y < gameState.height; y++) {
                if (gameState.grid[x][y] == CellType.SHEEP) {
                    float cx = offsetX + x * cellSize;
                    float cy = offsetY + y * cellSize;
                    float pop = popScaleFor(x, y);
                    drawSheep(canvas, cx, cy, pop);
                }
            }
        }
    }

    private void drawAnimatedWolves(Canvas canvas) {
        long elapsed = System.currentTimeMillis() - simulationStartTime;
        long totalDuration = getSimulationDurationMs();

        // Notify simulation listener when all wolf animations have finished
        if (totalDuration > 0 && elapsed >= totalDuration && !simulationFinishedNotified) {
            simulationFinishedNotified = true;
            if (simulationFinishedListener != null) {
                final boolean win = currentWolvesWin;
                post(() -> simulationFinishedListener.onSimulationFinished(win));
            }
        }

        long dashPhase = (elapsed / 20) % 40;
        dashPaint.setStrokeWidth(cellSize * 0.08f);
        dashPaint.setPathEffect(new DashPathEffect(new float[]{cellSize * 0.25f, cellSize * 0.15f}, dashPhase));

        for (int w = 0; w < wolfSims.size(); w++) {
            WolfSim wolf = wolfSims.get(w);
            if (wolf.path.isEmpty()) continue;

            // Wait until this specific wolf's sequential turn to start running
            if (elapsed < wolf.startMs) {
                continue;
            }

            long wolfElapsed = elapsed - wolf.startMs;
            int maxStep = wolf.path.size() - 1;

            // If this wolf did not catch sheep (it exited safely), once it leaps through
            // the exit gate and takes the exit step, it disappears into the forest!
            if (!wolf.reachesSheep && wolfElapsed >= wolf.durationMs + WOLF_STEP_MS) {
                continue;
            }

            float floatStep = wolfElapsed / (float) WOLF_STEP_MS;
            int currentStep = Math.min((int) floatStep, maxStep);
            float frac = Math.min(floatStep - currentStep, 1f);
            if (currentStep >= maxStep) {
                currentStep = maxStep;
                frac = 0;
            }

            // Draw dashed path trail only while wolf is actively running
            if (currentStep > 0 && (!wolf.reachesSheep || wolfElapsed < wolf.durationMs + 2000)) {
                path.reset();
                Coordinate startCoord = wolf.path.get(0);
                path.moveTo(offsetX + startCoord.x * cellSize + cellSize / 2f,
                        offsetY + startCoord.y * cellSize + cellSize / 2f);
                for (int s = 1; s <= currentStep; s++) {
                    Coordinate c = wolf.path.get(s);
                    path.lineTo(offsetX + c.x * cellSize + cellSize / 2f,
                            offsetY + c.y * cellSize + cellSize / 2f);
                }
                canvas.drawPath(path, dashPaint);
            }

            Coordinate from = wolf.path.get(currentStep);
            float x, y;
            float jumpHeight = 0f;

            if (currentStep < maxStep && frac > 0) {
                Coordinate to = wolf.path.get(currentStep + 1);
                x = offsetX + (from.x + (to.x - from.x) * frac) * cellSize;
                y = offsetY + (from.y + (to.y - from.y) * frac) * cellSize;
                if (currentStep == 0) {
                    // Dramatic leap over the entrance gate onto the field
                    jumpHeight = (float) Math.sin(frac * Math.PI) * cellSize * 0.75f;
                } else if (!wolf.reachesSheep && currentStep == maxStep - 1 && isWolfGate(to.x, to.y)) {
                    // Leap out over the boundary exit gate
                    jumpHeight = (float) Math.sin(frac * Math.PI) * cellSize * 0.75f;
                }
            } else {
                x = offsetX + from.x * cellSize;
                y = offsetY + from.y * cellSize;
            }

            float jumpScale = 1.0f;
            if (jumpHeight > 0) {
                jumpScale = 1.0f + 0.3f * (jumpHeight / (cellSize * 0.75f));
            }

            // Shrink/fade slightly as it leaves through the exit gate
            if (!wolf.reachesSheep && currentStep == maxStep && wolfElapsed > wolf.durationMs) {
                float exitFrac = Math.min((wolfElapsed - wolf.durationMs) / (float) WOLF_STEP_MS, 1f);
                jumpScale *= (1.0f - 0.35f * exitFrac);
            }

            drawWolf(canvas, x, y - jumpHeight, jumpScale);
        }
    }

    // ---------------------------------------------------------------------
    // Terrain
    // ---------------------------------------------------------------------

    private void drawGrassBackground(Canvas canvas, int width, int height) {
        // Soft base gradient (slightly lighter in the middle for depth)
        gradientPaint.setShader(new RadialGradient(
                width / 2f, height / 2.2f, Math.max(width, height) * 0.75f,
                new int[]{color(R_grass_light()), color(R_grass_base()), color(R_grass_base_dark())},
                new float[]{0f, 0.55f, 1f},
                Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, gradientPaint);
        gradientPaint.setShader(null);

        random.setSeed(20240501L);

        // Soft irregular dirt patches for variety (drawn first, under blades/flowers)
        int dirtPatches = Math.max(3, (int) ((width * height) / 90000f));
        for (int i = 0; i < dirtPatches; i++) {
            float rx = random.nextFloat() * width;
            float ry = random.nextFloat() * height;
            float r = 18 + random.nextFloat() * 28;
            gradientPaint.setShader(new RadialGradient(rx, ry, r,
                    Color.argb(70, 141, 110, 74), Color.argb(0, 141, 110, 74),
                    Shader.TileMode.CLAMP));
            canvas.drawCircle(rx, ry, r, gradientPaint);
            gradientPaint.setShader(null);
        }

        // Grass blades (small curved strokes, two tones)
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        int bladeCount = Math.max(80, (int) ((width * height) / 2600f));
        for (int i = 0; i < bladeCount; i++) {
            float rx = random.nextFloat() * width;
            float ry = random.nextFloat() * height;
            float len = 6 + random.nextFloat() * 10;
            float lean = (random.nextFloat() - 0.5f) * 10f;
            paint.setColor(random.nextBoolean() ? color(R_grass_blade_dark()) : color(R_grass_light()));
            paint.setStrokeWidth(1.6f + random.nextFloat() * 1.2f);
            path.reset();
            path.moveTo(rx, ry);
            path.quadTo(rx + lean * 0.5f, ry - len * 0.6f, rx + lean, ry - len);
            canvas.drawPath(path, paint);
        }
        paint.setStyle(Paint.Style.FILL);

        // Little flower clusters
        int flowerCount = Math.max(10, (int) ((width * height) / 42000f));
        for (int i = 0; i < flowerCount; i++) {
            float rx = random.nextFloat() * width;
            float ry = random.nextFloat() * height;
            drawFlower(canvas, rx, ry, 3.5f + random.nextFloat() * 2f);
        }

        // Small stones
        int stoneCount = Math.max(6, (int) ((width * height) / 60000f));
        for (int i = 0; i < stoneCount; i++) {
            float rx = random.nextFloat() * width;
            float ry = random.nextFloat() * height;
            drawPebble(canvas, rx, ry, 3f + random.nextFloat() * 3f);
        }

        // Gentle vignette so the field blends into the surrounding UI instead of
        // reading as a hard-edged rectangle.
        gradientPaint.setShader(new RadialGradient(
                width / 2f, height / 2f, Math.max(width, height) * 0.75f,
                new int[]{Color.argb(0, 0, 0, 0), Color.argb(0, 0, 0, 0), Color.argb(55, 15, 30, 10)},
                new float[]{0f, 0.78f, 1f},
                Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, gradientPaint);
        gradientPaint.setShader(null);
    }

    private void drawFlower(Canvas canvas, float cx, float cy, float petalR) {
        paint.setColor(color(R_grass_flower_yellow()));
        for (int p = 0; p < 5; p++) {
            double angle = (Math.PI * 2 / 5) * p;
            float px = cx + (float) Math.cos(angle) * petalR;
            float py = cy + (float) Math.sin(angle) * petalR;
            canvas.drawCircle(px, py, petalR * 0.65f, paint);
        }
        paint.setColor(color(R_grass_flower_center()));
        canvas.drawCircle(cx, cy, petalR * 0.55f, paint);
    }

    private void drawPebble(Canvas canvas, float cx, float cy, float r) {
        gradientPaint.setShader(new RadialGradient(cx - r * 0.3f, cy - r * 0.3f, r * 1.6f,
                color(R_grass_stone_light()), color(R_grass_stone_dark_c()),
                Shader.TileMode.CLAMP));
        path.reset();
        int points = 6;
        for (int i = 0; i < points; i++) {
            double a = (Math.PI * 2 / points) * i;
            float jitter = 0.75f + random.nextFloat() * 0.35f;
            float px = cx + (float) Math.cos(a) * r * jitter;
            float py = cy + (float) Math.sin(a) * r * jitter;
            if (i == 0) path.moveTo(px, py); else path.lineTo(px, py);
        }
        path.close();
        canvas.drawPath(path, gradientPaint);
        gradientPaint.setShader(null);
    }

    private void drawSubtleGridDots(Canvas canvas) {
        paint.setColor(Color.argb(26, 20, 40, 15));
        for (int x = 1; x < gameState.width; x++) {
            for (int y = 1; y < gameState.height; y++) {
                float px = offsetX + x * cellSize;
                float py = offsetY + y * cellSize;
                canvas.drawCircle(px, py, 1.6f, paint);
            }
        }
    }

    // ---------------------------------------------------------------------
    // Fences (wooden posts & double crossbars / перегородки)
    // ---------------------------------------------------------------------

    private void drawFence(Canvas canvas, int gridX, int gridY, float x, float y, float scale) {
        boolean up = (gridY > 0 && isFenceOrSpawn(gridX, gridY - 1));
        boolean down = (gridY < gameState.height - 1 && isFenceOrSpawn(gridX, gridY + 1));
        boolean left = (gridX > 0 && isFenceOrSpawn(gridX - 1, gridY));
        boolean right = (gridX < gameState.width - 1 && isFenceOrSpawn(gridX + 1, gridY));

        float cxCenter = x + cellSize / 2f;
        float cyCenter = y + cellSize / 2f;
        float postR = cellSize * 0.17f * scale;

        // If isolated cell (no connected neighbors): draw a complete 2-post hurdle with 2 crossbars
        // matching ic_icon_fence.xml so it never appears as an unfinished lone circular stump
        if (!up && !down && !left && !right) {
            drawStandaloneFenceHurdle(canvas, cxCenter, cyCenter, scale);
            return;
        }

        // Ground shadow under center post
        paint.setColor(Color.argb(60, 0, 0, 0));
        canvas.drawOval(cxCenter - postR * 1.3f, cyCenter - postR * 0.7f + postR * 1.5f,
                cxCenter + postR * 1.3f, cyCenter + postR * 1.1f + postR * 1.5f, paint);

        // Connecting double crossbars (2 parallel wooden rails with grass between them)
        float railOffset = cellSize * 0.125f * scale;
        float railThickness = cellSize * 0.085f * scale;

        if (up) {
            drawRailVertical(canvas, cxCenter - railOffset, cyCenter, y, railThickness);
            drawRailVertical(canvas, cxCenter + railOffset, cyCenter, y, railThickness);
        }
        if (down) {
            drawRailVertical(canvas, cxCenter - railOffset, cyCenter, y + cellSize, railThickness);
            drawRailVertical(canvas, cxCenter + railOffset, cyCenter, y + cellSize, railThickness);
        }
        if (left) {
            drawRailHorizontal(canvas, cxCenter, cyCenter - railOffset, x, railThickness);
            drawRailHorizontal(canvas, cxCenter, cyCenter + railOffset, x, railThickness);
        }
        if (right) {
            drawRailHorizontal(canvas, cxCenter, cyCenter - railOffset, x + cellSize, railThickness);
            drawRailHorizontal(canvas, cxCenter, cyCenter + railOffset, x + cellSize, railThickness);
        }

        drawPost(canvas, cxCenter, cyCenter, postR, true);
    }

    private void drawRailHorizontal(Canvas canvas, float x1, float cy, float x2, float thickness) {
        float left = Math.min(x1, x2);
        float right = Math.max(x1, x2);
        float top = cy - thickness / 2f;
        float bottom = cy + thickness / 2f;
        rectF.set(left, top, right, bottom);

        // Subtle drop shadow under rail
        paint.setColor(Color.argb(45, 20, 10, 5));
        canvas.drawRoundRect(left, top + thickness * 0.35f, right, bottom + thickness * 0.35f,
                thickness * 0.25f, thickness * 0.25f, paint);

        // Wood gradient (light warm amber top to rich wood brown bottom)
        gradientPaint.setShader(new LinearGradient(
                left, top, left, bottom,
                color(R_wood_plank_light()), color(R_wood_plank_dark()),
                Shader.TileMode.CLAMP));
        canvas.drawRoundRect(rectF, thickness * 0.25f, thickness * 0.25f, gradientPaint);
        gradientPaint.setShader(null);

        // Top edge golden highlight rim
        paint.setColor(Color.argb(130, 250, 208, 120));
        paint.setStrokeWidth(1.2f);
        canvas.drawLine(left, top + 0.8f, right, top + 0.8f, paint);

        // Bottom dark groove
        paint.setColor(Color.argb(90, 50, 24, 8));
        canvas.drawLine(left, bottom - 0.6f, right, bottom - 0.6f, paint);
    }

    private void drawRailVertical(Canvas canvas, float cx, float y1, float y2, float thickness) {
        float top = Math.min(y1, y2);
        float bottom = Math.max(y1, y2);
        float left = cx - thickness / 2f;
        float right = cx + thickness / 2f;
        rectF.set(left, top, right, bottom);

        // Shadow to the right
        paint.setColor(Color.argb(45, 20, 10, 5));
        canvas.drawRoundRect(left + thickness * 0.3f, top, right + thickness * 0.3f, bottom,
                thickness * 0.25f, thickness * 0.25f, paint);

        gradientPaint.setShader(new LinearGradient(
                left, top, right, top,
                color(R_wood_plank_light()), color(R_wood_plank_dark()),
                Shader.TileMode.CLAMP));
        canvas.drawRoundRect(rectF, thickness * 0.25f, thickness * 0.25f, gradientPaint);
        gradientPaint.setShader(null);

        // Left highlight line
        paint.setColor(Color.argb(130, 250, 208, 120));
        paint.setStrokeWidth(1.2f);
        canvas.drawLine(left + 0.8f, top, left + 0.8f, bottom, paint);

        // Right dark edge
        paint.setColor(Color.argb(90, 50, 24, 8));
        canvas.drawLine(right - 0.6f, top, right - 0.6f, bottom, paint);
    }

    private void drawStandaloneFenceHurdle(Canvas canvas, float cx, float cy, float scale) {
        float span = cellSize * 0.28f * scale;
        float p1X = cx - span;
        float p2X = cx + span;
        float postR = cellSize * 0.13f * scale;
        float railThickness = cellSize * 0.08f * scale;
        float railOffset = cellSize * 0.11f * scale;

        // Ground shadow under the entire hurdle
        paint.setColor(Color.argb(60, 0, 0, 0));
        canvas.drawOval(p1X - postR * 1.5f, cy + postR * 1.0f,
                p2X + postR * 1.5f, cy + postR * 2.0f, paint);

        // Two horizontal crossbars
        float railLeft = p1X - postR * 0.6f;
        float railRight = p2X + postR * 0.6f;
        drawRailHorizontal(canvas, railLeft, cy - railOffset, railRight, railThickness);
        drawRailHorizontal(canvas, railLeft, cy + railOffset, railRight, railThickness);

        // Left and Right mini-posts with caps and nails
        drawPost(canvas, p1X, cy, postR, true);
        drawPost(canvas, p2X, cy, postR, true);
    }

    private void drawPost(Canvas canvas, float cx, float cy, float r, boolean isJunction) {
        gradientPaint.setShader(new LinearGradient(
                cx - r, cy, cx + r, cy,
                color(R_wood_post_light()), color(R_wood_post_dark()),
                Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, r, gradientPaint);
        gradientPaint.setShader(null);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.4f);
        paint.setColor(color(R_wood_grain()));
        canvas.drawCircle(cx, cy, r, paint);
        // growth-ring texture
        canvas.drawCircle(cx, cy, r * 0.62f, paint);
        canvas.drawCircle(cx, cy, r * 0.26f, paint);
        paint.setStyle(Paint.Style.FILL);

        // Wood highlight bevel
        paint.setColor(Color.argb(100, 255, 230, 168));
        canvas.drawCircle(cx - r * 0.22f, cy - r * 0.22f, r * 0.28f, paint);

        if (isJunction) {
            // Wood peg nail in center
            paint.setColor(Color.argb(220, 41, 18, 3));
            canvas.drawCircle(cx, cy, r * 0.18f, paint);
        }
    }

    private boolean isFenceOrSpawn(int x, int y) {
        if (gameState == null || x < 0 || x >= gameState.width || y < 0 || y >= gameState.height) {
            return false;
        }
        if (isWolfGate(x, y)) {
            return false;
        }
        return gameState.grid[x][y] == CellType.FENCE;
    }


    private void drawWolfGate(Canvas canvas, int gridX, int gridY, float x, float y) {
        float cx = x + cellSize / 2f;
        float cy = y + cellSize / 2f;

        boolean isTop = (gridY == 0);
        boolean isBottom = (gameState != null && gridY == gameState.height - 1);
        boolean isLeft = (gridX == 0);
        boolean isRight = (gameState != null && gridX == gameState.width - 1);

        // 1. Deep trodden earthen road / entrance threshold
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(220, 115, 80, 48)); // Loam path
        canvas.drawRoundRect(x + cellSize * 0.06f, y + cellSize * 0.06f,
                x + cellSize * 0.94f, y + cellSize * 0.94f,
                cellSize * 0.22f, cellSize * 0.22f, paint);

        // Inner worn path surface
        paint.setColor(Color.argb(210, 162, 122, 78)); // Sandy dirt
        canvas.drawRoundRect(x + cellSize * 0.14f, y + cellSize * 0.14f,
                x + cellSize * 0.86f, y + cellSize * 0.86f,
                cellSize * 0.18f, cellSize * 0.18f, paint);

        // Cobblestones along the path
        paint.setColor(Color.argb(170, 195, 185, 170));
        float stoneR = cellSize * 0.055f;
        canvas.drawCircle(cx - cellSize * 0.22f, cy - cellSize * 0.18f, stoneR, paint);
        canvas.drawCircle(cx + cellSize * 0.22f, cy - cellSize * 0.16f, stoneR * 0.9f, paint);
        canvas.drawCircle(cx - cellSize * 0.20f, cy + cellSize * 0.20f, stoneR * 0.85f, paint);
        canvas.drawCircle(cx + cellSize * 0.20f, cy + cellSize * 0.18f, stoneR, paint);

        // 2. Heavy 3D Wooden Gate Posts on both sides of the gap
        float postR = cellSize * 0.13f;
        if (isTop || isBottom) {
            drawGatePost(canvas, x + cellSize * 0.14f, cy, postR);
            drawGatePost(canvas, x + cellSize * 0.86f, cy, postR);
        } else {
            drawGatePost(canvas, cx, y + cellSize * 0.14f, postR);
            drawGatePost(canvas, cx, y + cellSize * 0.86f, postR);
        }

        // Draw badge onto static layer as well (fallback & initial display)
        drawSingleGateBadge(canvas, gridX, gridY, cx, cy);
    }

    private void drawGatePost(Canvas canvas, float px, float py, float r) {
        // Drop shadow
        paint.setColor(Color.argb(120, 20, 10, 4));
        canvas.drawCircle(px + 2f, py + 3f, r, paint);

        // Post outer wood
        gradientPaint.setShader(new LinearGradient(
                px - r, py - r, px + r, py + r,
                color(R_wood_post_light()), color(R_wood_post_dark()),
                Shader.TileMode.CLAMP));
        canvas.drawCircle(px, py, r, gradientPaint);
        gradientPaint.setShader(null);

        // Dark grain ring
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.8f);
        paint.setColor(color(R_wood_grain()));
        canvas.drawCircle(px, py, r, paint);
        canvas.drawCircle(px, py, r * 0.6f, paint);
        canvas.drawCircle(px, py, r * 0.25f, paint);

        // Iron bracket ring
        paint.setStrokeWidth(2.2f);
        paint.setColor(Color.argb(220, 50, 50, 55));
        canvas.drawCircle(px, py, r * 0.85f, paint);

        paint.setStyle(Paint.Style.FILL);
    }

    private void drawWolfGateBadges(Canvas canvas) {
        if (gameState == null) return;

        for (int x = 0; x < gameState.width; x++) {
            for (int y = 0; y < gameState.height; y++) {
                if (isWolfGate(x, y)) {
                    float cx = offsetX + x * cellSize + cellSize / 2f;
                    float cy = offsetY + y * cellSize + cellSize / 2f;
                    drawSingleGateBadge(canvas, x, y, cx, cy);
                }
            }
        }
    }

    private void drawSingleGateBadge(Canvas canvas, int gridX, int gridY, float cx, float cy) {
        boolean isEntrance = false;
        if (gameState != null && gameState.wolfSpawns != null) {
            for (Coordinate spawn : gameState.wolfSpawns) {
                if (spawn.x == gridX && spawn.y == gridY) {
                    isEntrance = true;
                    break;
                }
            }
        }
        boolean isExit = false;
        if (gameState != null && gameState.wolfExits != null) {
            for (Coordinate exit : gameState.wolfExits) {
                if (exit.x == gridX && exit.y == gridY) {
                    isExit = true;
                    break;
                }
            }
        }

        if (!isEntrance && !isExit) return;

        boolean isTop = (gridY == 0);
        boolean isBottom = (gameState != null && gridY == gameState.height - 1);
        boolean isLeft = (gridX == 0);
        boolean isRight = (gameState != null && gridX == gameState.width - 1);

        String text;
        String arrow;
        if (isEntrance) {
            if (isTop) {
                arrow = "↓";
            } else if (isBottom) {
                arrow = "↑";
            } else if (isLeft) {
                arrow = "→";
            } else {
                arrow = "←";
            }
            text = "ВХОД " + arrow;
        } else {
            if (isTop) {
                arrow = "↑";
            } else if (isBottom) {
                arrow = "↓";
            } else if (isLeft) {
                arrow = "←";
            } else {
                arrow = "→";
            }
            text = "ВЫХОД " + arrow;
        }

        // 3D Placard Badge Dimensions
        float bw = cellSize * 0.94f;
        float bh = cellSize * 0.46f;
        float bx1 = cx - bw / 2f;
        float by1 = cy - bh / 2f;
        float bx2 = cx + bw / 2f;
        float by2 = cy + bh / 2f;
        float cornerR = cellSize * 0.14f;

        // 1. Soft pulsing beacon halo
        long time = System.currentTimeMillis() - startTime;
        float pulse = (float) (0.5 + 0.5 * Math.sin(time / 280.0));
        paint.setStyle(Paint.Style.FILL);
        if (isEntrance) {
            paint.setColor(Color.argb((int) (55 + pulse * 65), 255, 120, 0));
        } else {
            paint.setColor(Color.argb((int) (55 + pulse * 65), 76, 175, 80));
        }
        canvas.drawRoundRect(bx1 - 4f, by1 - 4f, bx2 + 4f, by2 + 4f, cornerR + 3f, cornerR + 3f, paint);

        // 2. Heavy 3D Drop Shadow
        paint.setColor(Color.argb(180, 20, 10, 4));
        canvas.drawRoundRect(bx1, by1 + 4f, bx2, by2 + 4f, cornerR, cornerR, paint);

        // 3. Dark Outer Wood/Iron Bevel Rim
        paint.setColor(Color.argb(255, 45, 20, 8));
        canvas.drawRoundRect(bx1, by1, bx2, by2, cornerR, cornerR, paint);

        // 4. Golden Bevel Inner Frame
        paint.setColor(isEntrance ? Color.rgb(255, 205, 80) : Color.rgb(180, 235, 120));
        canvas.drawRoundRect(bx1 + 2f, by1 + 2f, bx2 - 2f, by2 - 2f, cornerR - 1f, cornerR - 1f, paint);

        // 5. Rich Enamel Badge Face Gradient
        if (isEntrance) {
            // Bright fiery crimson/amber
            gradientPaint.setShader(new LinearGradient(
                    bx1, by1, bx1, by2,
                    Color.rgb(235, 55, 30), Color.rgb(165, 25, 12),
                    Shader.TileMode.CLAMP));
        } else {
            // Vibrant emerald green
            gradientPaint.setShader(new LinearGradient(
                    bx1, by1, bx1, by2,
                    Color.rgb(46, 160, 55), Color.rgb(20, 100, 30),
                    Shader.TileMode.CLAMP));
        }
        canvas.drawRoundRect(bx1 + 3.5f, by1 + 3.5f, bx2 - 3.5f, by2 - 3.5f, cornerR - 2.5f, cornerR - 2.5f, gradientPaint);
        gradientPaint.setShader(null);

        // 6. Top Gloss Highlight
        gradientPaint.setShader(new LinearGradient(
                bx1, by1 + 3.5f, bx1, by1 + bh * 0.45f,
                Color.argb(120, 255, 255, 255), Color.argb(0, 255, 255, 255),
                Shader.TileMode.CLAMP));
        canvas.drawRoundRect(bx1 + 4f, by1 + 3.5f, bx2 - 4f, by1 + bh * 0.45f, cornerR - 3f, cornerR - 3f, gradientPaint);
        gradientPaint.setShader(null);

        // 7. Crisp High-Contrast Bold Text
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setFakeBoldText(true);
        paint.setTextSize(cellSize * 0.22f);
        paint.setLetterSpacing(0.04f);

        // Text Drop Shadow (Deep black)
        paint.setColor(Color.argb(250, 15, 5, 2));
        Paint.FontMetrics fm = paint.getFontMetrics();
        float textY = cy - (fm.descent + fm.ascent) / 2f;
        canvas.drawText(text, cx, textY + 2.5f, paint);

        // Text Face (Pure Crisp White)
        paint.setColor(Color.WHITE);
        canvas.drawText(text, cx, textY, paint);

        paint.setFakeBoldText(false);
        paint.setLetterSpacing(0f);
    }

    // ---------------------------------------------------------------------
    // Sheep
    // ---------------------------------------------------------------------

    public void drawSheep(Canvas canvas, float x, float y) {
        drawSheep(canvas, x, y, 1f);
    }

    public void drawSheep(Canvas canvas, float x, float y, float popScale) {
        long time = System.currentTimeMillis() - startTime;
        float breathe = (float) Math.sin(time / 260.0) * cellSize * 0.02f;
        float headWobble = (float) Math.sin(time / 340.0 + 1) * 4f; // degrees
        float legSway = (float) Math.sin(time / 220.0) * cellSize * 0.015f;

        float cx = x + cellSize * 0.5f;
        float cy = y + cellSize * 0.5f + breathe;

        canvas.save();
        if (popScale != 1f) {
            canvas.scale(popScale, popScale, cx, cy);
        }

        // Shadow
        paint.setColor(Color.argb(70, 0, 0, 0));
        canvas.drawOval(x + cellSize * 0.22f, y + cellSize * 0.34f + cellSize * 0.05f,
                x + cellSize * 0.78f, y + cellSize * 0.7f + cellSize * 0.05f, paint);

        // Legs peeking from under the body
        paint.setColor(color(R_sheep_leg()));
        float legW = cellSize * 0.045f;
        float legY0 = cy + cellSize * 0.16f;
        float legY1 = cy + cellSize * 0.27f + Math.abs(legSway);
        canvas.drawRoundRect(cx - cellSize * 0.22f, legY0, cx - cellSize * 0.22f + legW, legY1, legW / 2, legW / 2, paint);
        canvas.drawRoundRect(cx - cellSize * 0.08f, legY0, cx - cellSize * 0.08f + legW, legY1 - legSway, legW / 2, legW / 2, paint);
        canvas.drawRoundRect(cx + cellSize * 0.06f, legY0, cx + cellSize * 0.06f + legW, legY1 + legSway, legW / 2, legW / 2, paint);
        canvas.drawRoundRect(cx + cellSize * 0.20f, legY0, cx + cellSize * 0.20f + legW, legY1, legW / 2, legW / 2, paint);

        // Fluffy wool body - layered lobes with soft shading for a "cloud" look
        gradientPaint.setShader(new RadialGradient(cx - cellSize * 0.05f, cy - cellSize * 0.08f, cellSize * 0.32f,
                color(R_sheep_wool()), color(R_sheep_wool_shadow()), Shader.TileMode.CLAMP));
        float[][] lobes = {
                {0.30f, 0.5f, 0.20f}, {0.70f, 0.5f, 0.20f}, {0.5f, 0.30f, 0.20f},
                {0.5f, 0.70f, 0.20f}, {0.38f, 0.36f, 0.15f}, {0.62f, 0.36f, 0.15f},
                {0.38f, 0.64f, 0.15f}, {0.62f, 0.64f, 0.15f}, {0.5f, 0.5f, 0.25f}
        };
        for (float[] lobe : lobes) {
            canvas.drawCircle(x + cellSize * lobe[0], y + cellSize * lobe[1] + breathe, cellSize * lobe[2], gradientPaint);
        }
        gradientPaint.setShader(null);

        // Head (with small idle wobble)
        float headCx = x + cellSize * 0.80f;
        float headCy = y + cellSize * 0.5f + breathe;
        canvas.save();
        canvas.rotate(headWobble, headCx - cellSize * 0.1f, headCy);

        // Ears
        paint.setColor(color(R_sheep_face_light()));
        canvas.drawOval(headCx - cellSize * 0.02f, headCy - cellSize * 0.2f, headCx + cellSize * 0.1f, headCy - cellSize * 0.06f, paint);
        canvas.drawOval(headCx - cellSize * 0.02f, headCy + cellSize * 0.06f, headCx + cellSize * 0.1f, headCy + cellSize * 0.2f, paint);

        // Face
        paint.setColor(color(R_sheep_face()));
        canvas.drawCircle(headCx, headCy, cellSize * 0.155f, paint);

        // Eyes
        paint.setColor(Color.WHITE);
        canvas.drawCircle(headCx + cellSize * 0.03f, headCy - cellSize * 0.05f, cellSize * 0.035f, paint);
        canvas.drawCircle(headCx + cellSize * 0.03f, headCy + cellSize * 0.05f, cellSize * 0.035f, paint);
        paint.setColor(Color.BLACK);
        canvas.drawCircle(headCx + cellSize * 0.045f, headCy - cellSize * 0.05f, cellSize * 0.015f, paint);
        canvas.drawCircle(headCx + cellSize * 0.045f, headCy + cellSize * 0.05f, cellSize * 0.015f, paint);

        // Nose
        paint.setColor(Color.argb(180, 20, 15, 12));
        canvas.drawCircle(headCx + cellSize * 0.13f, headCy, cellSize * 0.02f, paint);

        canvas.restore(); // head rotation
        canvas.restore(); // pop scale
    }

    // ---------------------------------------------------------------------
    // Wolves
    // ---------------------------------------------------------------------

    public void drawWolf(Canvas canvas, float x, float y) {
        drawWolf(canvas, x, y, 1f);
    }

    public void drawWolf(Canvas canvas, float x, float y, float scale) {
        if (scale != 1.0f) {
            canvas.save();
            float cx = x + cellSize * 0.5f;
            float cy = y + cellSize * 0.5f;
            canvas.scale(scale, scale, cx, cy);
            drawWolfInternal(canvas, x, y);
            canvas.restore();
        } else {
            drawWolfInternal(canvas, x, y);
        }
    }

    private void drawWolfInternal(Canvas canvas, float x, float y) {
        long time = System.currentTimeMillis() - startTime;
        float bounce = (float) Math.abs(Math.sin(time / 150.0)) * cellSize * 0.08f;
        float stepPhase = (float) Math.sin(time / 120.0);
        y -= bounce;

        float cx = x + cellSize * 0.5f;
        float cy = y + cellSize * 0.5f;

        // Shadow
        paint.setColor(Color.argb(75, 0, 0, 0));
        canvas.drawOval(x + cellSize * 0.14f, y + cellSize * 0.34f + bounce + cellSize * 0.08f,
                x + cellSize * 0.86f, y + cellSize * 0.68f + bounce + cellSize * 0.08f, paint);

        // Legs (alternating stepping motion)
        paint.setColor(color(R_wolf_body_dark()));
        float legW = cellSize * 0.05f;
        float frontLegLift = Math.max(0, stepPhase) * cellSize * 0.06f;
        float backLegLift = Math.max(0, -stepPhase) * cellSize * 0.06f;
        canvas.drawRoundRect(cx - cellSize * 0.26f, cy + cellSize * 0.14f - backLegLift, cx - cellSize * 0.26f + legW, cy + cellSize * 0.30f - backLegLift, legW / 2, legW / 2, paint);
        canvas.drawRoundRect(cx - cellSize * 0.12f, cy + cellSize * 0.16f - frontLegLift, cx - cellSize * 0.12f + legW, cy + cellSize * 0.30f - frontLegLift, legW / 2, legW / 2, paint);
        canvas.drawRoundRect(cx + cellSize * 0.05f, cy + cellSize * 0.16f - backLegLift, cx + cellSize * 0.05f + legW, cy + cellSize * 0.30f - backLegLift, legW / 2, legW / 2, paint);
        canvas.drawRoundRect(cx + cellSize * 0.19f, cy + cellSize * 0.14f - frontLegLift, cx + cellSize * 0.19f + legW, cy + cellSize * 0.30f - frontLegLift, legW / 2, legW / 2, paint);

        // Tail
        paint.setColor(color(R_wolf_body_dark()));
        canvas.save();
        canvas.rotate(-20 + stepPhase * 6, x + cellSize * 0.16f, cy);
        canvas.drawRoundRect(x + cellSize * 0.02f, cy - cellSize * 0.05f, x + cellSize * 0.2f, cy + cellSize * 0.05f, cellSize * 0.04f, cellSize * 0.04f, paint);
        canvas.restore();

        // Body with gradient for volume
        gradientPaint.setShader(new LinearGradient(cx, y + cellSize * 0.32f, cx, y + cellSize * 0.68f,
                color(R_wolf_body_light()), color(R_wolf_body()), Shader.TileMode.CLAMP));
        canvas.drawRoundRect(x + cellSize * 0.20f, y + cellSize * 0.34f, x + cellSize * 0.80f, y + cellSize * 0.66f, cellSize * 0.12f, cellSize * 0.12f, gradientPaint);
        gradientPaint.setShader(null);

        // Belly stripe
        paint.setColor(color(R_wolf_belly()));
        canvas.drawRoundRect(x + cellSize * 0.28f, y + cellSize * 0.52f, x + cellSize * 0.72f, y + cellSize * 0.62f, cellSize * 0.05f, cellSize * 0.05f, paint);

        // Back fur tuft
        path.reset();
        path.moveTo(cx - cellSize * 0.05f, y + cellSize * 0.32f);
        path.lineTo(cx + cellSize * 0.02f, y + cellSize * 0.22f);
        path.lineTo(cx + cellSize * 0.09f, y + cellSize * 0.33f);
        path.close();
        paint.setColor(color(R_wolf_body_dark()));
        canvas.drawPath(path, paint);

        // Head
        float headCx = x + cellSize * 0.80f;
        float headCy = y + cellSize * 0.5f;
        paint.setColor(color(R_wolf_face()));
        canvas.drawCircle(headCx, headCy, cellSize * 0.19f, paint);

        // Ears
        paint.setColor(color(R_wolf_body_dark()));
        canvas.drawCircle(headCx - cellSize * 0.05f, headCy - cellSize * 0.17f, cellSize * 0.07f, paint);
        canvas.drawCircle(headCx - cellSize * 0.05f, headCy + cellSize * 0.17f, cellSize * 0.07f, paint);

        // Snout
        paint.setColor(color(R_wolf_body_dark()));
        canvas.drawCircle(headCx + cellSize * 0.16f, headCy, cellSize * 0.09f, paint);
        paint.setColor(Color.BLACK);
        canvas.drawCircle(headCx + cellSize * 0.22f, headCy, cellSize * 0.025f, paint);

        // Eyes (amber, a bit menacing)
        paint.setColor(color(R_wolf_eye()));
        canvas.drawCircle(headCx + cellSize * 0.05f, headCy - cellSize * 0.05f, cellSize * 0.03f, paint);
        canvas.drawCircle(headCx + cellSize * 0.05f, headCy + cellSize * 0.05f, cellSize * 0.03f, paint);
    }

    // ---------------------------------------------------------------------
    // Drag ghost + validity highlight
    // ---------------------------------------------------------------------

    private void drawGhost(Canvas canvas) {
        if (draggedType == null) return;

        int[] location = new int[2];
        getLocationOnScreen(location);
        float localX = dragX - location[0];
        float localY = dragY - location[1];

        int gridX = -1;
        int gridY = -1;
        if (cellSize > 0) {
            gridX = (int) ((localX - offsetX) / cellSize);
            gridY = (int) ((localY - offsetY) / cellSize);
        }

        if (draggedType == CellType.SHEEP) {
            boolean inBounds = gridX >= 0 && gridX < gameState.width && gridY >= 0 && gridY < gameState.height;
            boolean isValid = inBounds && isInnerPasture(gridX, gridY);
            float drawX, drawY;
            if (inBounds) {
                drawX = offsetX + gridX * cellSize;
                drawY = offsetY + gridY * cellSize;

                paint.setStyle(Paint.Style.FILL);
                paint.setColor(isValid ? color(R_valid_highlight()) : color(R_invalid_highlight()));
                canvas.drawRoundRect(drawX + 2, drawY + 2, drawX + cellSize - 2, drawY + cellSize - 2, 10, 10, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(3f);
                paint.setColor(isValid ? Color.argb(220, 76, 175, 80) : Color.argb(220, 229, 57, 53));
                canvas.drawRoundRect(drawX + 2, drawY + 2, drawX + cellSize - 2, drawY + cellSize - 2, 10, 10, paint);
                paint.setStyle(Paint.Style.FILL);
            } else {
                drawX = localX - cellSize / 2;
                drawY = localY - cellSize / 2;
            }

            int savedAlpha = paint.getAlpha();
            paint.setAlpha(190);
            drawSheep(canvas, drawX, drawY);
            paint.setAlpha(savedAlpha);

        } else if (draggedType == CellType.FENCE) {
            boolean inBounds = gridX >= 0 && gridX < gameState.width && gridY >= 0 && gridY < gameState.height;
            if (inBounds) {
                int x1 = gridX;
                int y1 = gridY;
                if (fenceHorizontal && x1 >= gameState.width - 2) x1 = gameState.width - 3;
                if (!fenceHorizontal && y1 >= gameState.height - 2) y1 = gameState.height - 3;
                int x2 = fenceHorizontal ? x1 + 1 : x1;
                int y2 = fenceHorizontal ? y1 : y1 + 1;

                boolean v1 = x1 >= 0 && x1 < gameState.width && y1 >= 0 && y1 < gameState.height && isTileValidForFence(x1, y1);
                boolean v2 = x2 >= 0 && x2 < gameState.width && y2 >= 0 && y2 < gameState.height && isTileValidForFence(x2, y2);
                boolean bothValid = v1 && v2;

                float drawX1 = offsetX + x1 * cellSize;
                float drawY1 = offsetY + y1 * cellSize;
                float drawX2 = offsetX + x2 * cellSize;
                float drawY2 = offsetY + y2 * cellSize;

                int fillColor = bothValid ? color(R_valid_highlight()) : color(R_invalid_highlight());
                int strokeColor = bothValid ? Color.argb(220, 76, 175, 80) : Color.argb(220, 229, 57, 53);

                paint.setStyle(Paint.Style.FILL);
                paint.setColor(fillColor);
                canvas.drawRoundRect(drawX1 + 2, drawY1 + 2, drawX1 + cellSize - 2, drawY1 + cellSize - 2, 10, 10, paint);
                canvas.drawRoundRect(drawX2 + 2, drawY2 + 2, drawX2 + cellSize - 2, drawY2 + cellSize - 2, 10, 10, paint);

                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(3f);
                paint.setColor(strokeColor);
                canvas.drawRoundRect(drawX1 + 2, drawY1 + 2, drawX1 + cellSize - 2, drawY1 + cellSize - 2, 10, 10, paint);
                canvas.drawRoundRect(drawX2 + 2, drawY2 + 2, drawX2 + cellSize - 2, drawY2 + cellSize - 2, 10, 10, paint);
                paint.setStyle(Paint.Style.FILL);

                int savedAlpha = paint.getAlpha();
                paint.setAlpha(190);
                drawGhostDoubleFence(canvas, x1, y1, x2, y2);
                paint.setAlpha(savedAlpha);
            } else {
                int savedAlpha = paint.getAlpha();
                paint.setAlpha(190);
                drawFloatingDoubleFence(canvas, localX, localY, fenceHorizontal);
                paint.setAlpha(savedAlpha);
            }
        }
    }

    private void drawGhostDoubleFence(Canvas canvas, int x1, int y1, int x2, int y2) {
        float cx1 = offsetX + x1 * cellSize + cellSize / 2f;
        float cy1 = offsetY + y1 * cellSize + cellSize / 2f;
        float cx2 = offsetX + x2 * cellSize + cellSize / 2f;
        float cy2 = offsetY + y2 * cellSize + cellSize / 2f;
        float postR = cellSize * 0.17f;
        float railThickness = cellSize * 0.085f;
        float railOffset = cellSize * 0.125f;

        if (y1 == y2) { // Horizontal
            float left = Math.min(cx1, cx2);
            float right = Math.max(cx1, cx2);
            drawRailHorizontal(canvas, left, cy1 - railOffset, right, railThickness);
            drawRailHorizontal(canvas, left, cy1 + railOffset, right, railThickness);
        } else { // Vertical
            float top = Math.min(cy1, cy2);
            float bottom = Math.max(cy1, cy2);
            drawRailVertical(canvas, cx1 - railOffset, top, bottom, railThickness);
            drawRailVertical(canvas, cx1 + railOffset, top, bottom, railThickness);
        }

        drawPost(canvas, cx1, cy1, postR, true);
        drawPost(canvas, cx2, cy2, postR, true);
    }

    private void drawFloatingDoubleFence(Canvas canvas, float cx, float cy, boolean horizontal) {
        float span = cellSize * 0.45f;
        float postR = cellSize * 0.15f;
        float railThickness = cellSize * 0.08f;
        float railOffset = cellSize * 0.12f;

        if (horizontal) {
            float p1X = cx - span;
            float p2X = cx + span;
            drawRailHorizontal(canvas, p1X, cy - railOffset, p2X, railThickness);
            drawRailHorizontal(canvas, p1X, cy + railOffset, p2X, railThickness);
            drawPost(canvas, p1X, cy, postR, true);
            drawPost(canvas, p2X, cy, postR, true);
        } else {
            float p1Y = cy - span;
            float p2Y = cy + span;
            drawRailVertical(canvas, cx - railOffset, p1Y, p2Y, railThickness);
            drawRailVertical(canvas, cx + railOffset, p1Y, p2Y, railThickness);
            drawPost(canvas, cx, p1Y, postR, true);
            drawPost(canvas, cx, p2Y, postR, true);
        }
    }

    private void drawSnapPulses(Canvas canvas) {
        long now = System.currentTimeMillis();
        for (int i = recentPlacements.size() - 1; i >= 0; i--) {
            PlacedAnim p = recentPlacements.get(i);
            long elapsed = now - p.time;
            if (elapsed > POP_DURATION_MS) {
                recentPlacements.remove(i);
                continue;
            }
            if (p.type != CellType.FENCE) continue; // sheep already animate via popScale in drawSheep
            float t = elapsed / (float) POP_DURATION_MS;
            float ccx = offsetX + p.x * cellSize + cellSize / 2f;
            float ccy = offsetY + p.y * cellSize + cellSize / 2f;
            float radius = cellSize * (0.25f + t * 0.4f);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3f * (1f - t));
            paint.setColor(Color.argb((int) (180 * (1f - t)), 255, 224, 130));
            canvas.drawCircle(ccx, ccy, radius, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    // ---------------------------------------------------------------------
    // Color helpers (resolve app colors without needing a Resources lookup
    // scattered through the drawing code)
    // ---------------------------------------------------------------------

    private int color(int argb) { return argb; }

    private int c(int colorRes) { return ContextCompat.getColor(getContext(), colorRes); }

    private int R_grass_base() { return c(R.color.grass_base); }
    private int R_grass_base_dark() { return c(R.color.grass_base_dark); }
    private int R_grass_light() { return c(R.color.grass_light); }
    private int R_grass_blade_dark() { return c(R.color.grass_blade_dark); }
    private int R_grass_flower_yellow() { return c(R.color.grass_flower_yellow); }
    private int R_grass_flower_center() { return c(R.color.grass_flower_center); }
    private int R_grass_stone_light() { return c(R.color.grass_stone); }
    private int R_grass_stone_dark_c() { return c(R.color.grass_stone_dark); }
    private int R_grass_dirt_patch() { return c(R.color.grass_dirt_patch); }

    private int R_wood_post_light() { return c(R.color.wood_post_light); }
    private int R_wood_post_dark() { return c(R.color.wood_post_dark); }
    private int R_wood_plank_light() { return c(R.color.wood_plank_light); }
    private int R_wood_plank_dark() { return c(R.color.wood_plank_dark); }
    private int R_wood_grain() { return c(R.color.wood_grain); }

    private int R_sheep_wool() { return c(R.color.sheep_wool); }
    private int R_sheep_wool_shadow() { return c(R.color.sheep_wool_shadow); }
    private int R_sheep_face() { return c(R.color.sheep_face); }
    private int R_sheep_face_light() { return c(R.color.sheep_face_light); }
    private int R_sheep_leg() { return c(R.color.sheep_leg); }

    private int R_wolf_body() { return c(R.color.wolf_body); }
    private int R_wolf_body_dark() { return c(R.color.wolf_body_dark); }
    private int R_wolf_body_light() { return c(R.color.wolf_body_light); }
    private int R_wolf_belly() { return c(R.color.wolf_belly); }
    private int R_wolf_face() { return c(R.color.wolf_face); }
    private int R_wolf_eye() { return c(R.color.wolf_eye); }

    private int R_valid_highlight() { return c(R.color.valid_highlight); }
    private int R_invalid_highlight() { return c(R.color.invalid_highlight); }

    public float getCellSize() { return cellSize; }
    public float getOffsetX() { return offsetX; }
    public float getOffsetY() { return offsetY; }
}
