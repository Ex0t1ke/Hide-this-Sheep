package com.example.myapplication;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class GameState {
    public int width = 10;
    public int height = 10;
    public CellType[][] grid;

    public int availableSheep = 1;
    public int availableFences = 3;

    /** Total fences given at level start (for scoring). */
    public int initialFences = 3;
    /** How many fences the player actually placed. */
    public int usedFences = 0;

    public GameStatus status = GameStatus.LEVEL_START;

    public List<Coordinate> wolfSpawns = new ArrayList<>();
    public List<Coordinate> wolfExits = new ArrayList<>();

    public int levelNumber = 1;
    public boolean isEndless = false;

    /** Score for the current wave/level (calculated on win). */
    public int levelScore = 0;

    public GameState() {
        this(1, false);
    }

    public GameState(int level, boolean isEndless) {
        this.levelNumber = level;
        this.isEndless = isEndless;
        grid = new CellType[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                grid[x][y] = CellType.EMPTY;
            }
        }
        if (isEndless) setupEndless(level);
        else setupLevel(level);
    }

    private void setupLevel(int level) {
        CampaignLevels.LevelConfig config = CampaignLevels.getLevel(level);
        availableSheep = config.sheepCount;
        availableFences = config.fencesCount;
        initialFences = availableFences;

        initBoundaryWalls();

        wolfSpawns.clear();
        wolfExits.clear();

        for (int i = 0; i < config.wolfSpawns.size(); i++) {
            Coordinate in = config.wolfSpawns.get(i);
            Coordinate out = config.wolfExits.get(i);
            if (in.x >= 0 && in.x < width && in.y >= 0 && in.y < height) {
                wolfSpawns.add(in);
            }
            if (out.x >= 0 && out.x < width && out.y >= 0 && out.y < height) {
                wolfExits.add(out);
            }
        }

        for (Coordinate f : config.staticFences) {
            if (f.x >= 0 && f.x < width && f.y >= 0 && f.y < height) {
                grid[f.x][f.y] = CellType.FENCE;
            }
        }

        status = GameStatus.LEVEL_START;
    }

    private void setupEndless(int wave) {
        availableSheep = (wave >= 12) ? 2 : 1;

        // Inventory fences: 8 for 1 sheep, 12 for 2 sheep
        if (wave >= 12) {
            availableFences = 12;
        } else {
            availableFences = 8;
        }
        initialFences = availableFences;

        initBoundaryWalls();

        Random rand = new Random(System.currentTimeMillis() + wave * 49999L + 777L);
        int gateCount = (wave >= 14) ? 3 : (wave >= 6 ? 2 : 1);
        spawnRandomWolfGates(rand, gateCount);
        generateFieldObstacles(rand, wave);

        status = GameStatus.PLACE_SHEEP;
    }

    private void initBoundaryWalls() {
        for (int x = 0; x < width; x++) {
            grid[x][0] = CellType.FENCE;
            grid[x][height - 1] = CellType.FENCE;
        }
        for (int y = 0; y < height; y++) {
            grid[0][y] = CellType.FENCE;
            grid[width - 1][y] = CellType.FENCE;
        }
    }

    /**
     * Determines whether a tile belongs to the outer buffer/dead-zone where players
     * cannot place fences to prevent blocking wolf spawn points.
     */
    public boolean isBufferZone(int x, int y) {
        if (x <= 1 || x >= width - 2 || y <= 1 || y >= height - 2) {
            return true;
        }
        return false;
    }

    /**
     * Returns the interior landing coordinate where the wolf arrives after jumping
     * over the perimeter fence.
     */
    public Coordinate getWolfLandingCoord(Coordinate spawn) {
        if (spawn == null) return new Coordinate(width / 2, 1);
        int lx = spawn.x;
        int ly = spawn.y;
        if (ly <= 0) ly = 1;
        else if (ly >= height - 1) ly = height - 2;
        else if (lx <= 0) lx = 1;
        else if (lx >= width - 1) lx = width - 2;
        return new Coordinate(lx, ly);
    }

    /**
     * Spawns wolf invasion points along the solid boundary fence.
     */
    private void spawnRandomWolfGates(Random rand, int count) {
        List<Integer> walls = new ArrayList<>();
        for (int i = 0; i < 4; i++) walls.add(i);
        Collections.shuffle(walls, rand);

        int actualGates = Math.min(count, 2);
        for (int i = 0; i < actualGates; i++) {
            int inWall = walls.get(i);
            int outWall = getOppositeWall(inWall);

            Coordinate inCoord = getWallCoordinate(inWall, 3 + rand.nextInt(4));
            Coordinate outCoord = getWallCoordinate(outWall, 3 + rand.nextInt(4));

            wolfSpawns.add(inCoord);
            wolfExits.add(outCoord);
        }
    }

    private int getOppositeWall(int wall) {
        switch (wall) {
            case 0: return 1; // Top -> Bottom
            case 1: return 0; // Bottom -> Top
            case 2: return 3; // Left -> Right
            default: return 2; // Right -> Left
        }
    }

    private Coordinate getWallCoordinate(int wall, int pos) {
        switch (wall) {
            case 0: return new Coordinate(pos, 0); // Top
            case 1: return new Coordinate(pos, height - 1); // Bottom
            case 2: return new Coordinate(0, pos); // Left
            default: return new Coordinate(width - 1, pos); // Right
        }
    }

    /**
     * Generates natural obstacles on the field as short 2-3 tile clusters
     * (never a single isolated post, which looked like an unfinished colышек).
     * Level 1 stays 100% clean so the player learns to build a full corral from scratch.
     * From wave 2 onward, clusters spawn as straight 2-cell, straight 3-cell, or 3-cell L-corners.
     * All cells of each cluster are validated and committed atomically.
     */
    private void generateFieldObstacles(Random rand, int level) {
        if (level == 1) return;
        int clusterCount = Math.min(1 + level / 4, 4);
        for (int retry = 0; retry < 10; retry++) {
            for (int x = 2; x < width - 2; x++) for (int y = 2; y < height - 2; y++) grid[x][y] = CellType.EMPTY;
            int placed = 0, attempts = 0;
            while (placed < clusterCount && attempts < 40) {
                attempts++;
                int cx = 2 + rand.nextInt(6), cy = 2 + rand.nextInt(6), shape = rand.nextInt(3);
                boolean horizontal = rand.nextBoolean();
                List<Coordinate> cluster = new ArrayList<>();
                if (shape == 0) { cluster.add(new Coordinate(cx, cy)); cluster.add(new Coordinate(horizontal ? cx + 1 : cx, horizontal ? cy : cy + 1)); }
                else if (shape == 1) { cluster.add(new Coordinate(cx, cy)); cluster.add(new Coordinate(horizontal ? cx + 1 : cx, horizontal ? cy : cy + 1)); cluster.add(new Coordinate(horizontal ? cx + 2 : cx, horizontal ? cy : cy + 2)); }
                else { cluster.add(new Coordinate(cx, cy)); cluster.add(new Coordinate(horizontal ? cx + 1 : cx, horizontal ? cy : cy + 1)); cluster.add(new Coordinate(cx, cy + 1)); }
                boolean valid = true;
                for (Coordinate c : cluster) {
                    if (c.x < 2 || c.x >= width - 2 || c.y < 2 || c.y >= height - 2 || grid[c.x][c.y] == CellType.FENCE) { valid = false; break; }
                    for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) {
                        int nx = c.x + dx, ny = c.y + dy;
                        if (nx >= 2 && nx < width - 2 && ny >= 2 && ny < height - 2 && grid[nx][ny] == CellType.FENCE) valid = false;
                    }
                }
                if (valid) { for (Coordinate c : cluster) grid[c.x][c.y] = CellType.FENCE; placed++; }
            }
            Coordinate dummySheep = new Coordinate(width / 2, height / 2);
            if (grid[dummySheep.x][dummySheep.y] == CellType.EMPTY) {
                grid[dummySheep.x][dummySheep.y] = CellType.SHEEP;
                boolean reachable = true;
                for (Coordinate spawn : wolfSpawns) {
                    if (!Pathfinder.canWolfReachSheep(this, getWolfLandingCoord(spawn))) { reachable = false; break; }
                }
                grid[dummySheep.x][dummySheep.y] = CellType.EMPTY;
                if (reachable) break;
            }
        }
    }

    private boolean canPlaceFieldFence(int x, int y) {
        if (x < 2 || x > width - 3 || y < 2 || y > height - 3) return false;
        if (grid[x][y] != CellType.EMPTY) return false;
        for (Coordinate spawn : wolfSpawns) {
            if (Math.abs(spawn.x - x) <= 1 && Math.abs(spawn.y - y) <= 1) return false;
        }
        for (Coordinate exit : wolfExits) {
            if (Math.abs(exit.x - x) <= 1 && Math.abs(exit.y - y) <= 1) return false;
        }
        return true;
    }

    /**
     * Calculate score for the current level/wave.
     * Base: 100 * level. Bonus: savedFences * 25 * multiplier.
     */
    public int calculateScore() {
        int savedFences = availableFences;
        int base = 100 * levelNumber;
        float multiplier = 1f + (levelNumber / 5f);
        int bonus = (int) (savedFences * 25 * multiplier);
        levelScore = base + bonus;
        return levelScore;
    }

    /**
     * Returns star rating (1-3) based on fence economy.
     */
    public int getStarRating() {
        if (initialFences <= 0) return 3;
        float usageRatio = (float) usedFences / initialFences;
        if (usageRatio <= 0.5f) return 3;
        if (usageRatio <= 0.75f) return 2;
        return 1;
    }
}