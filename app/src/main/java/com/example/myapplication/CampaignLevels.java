package com.example.myapplication;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Hand-crafted campaign levels with unique spatial puzzles and obstacles.
 *
 * Static obstacle fences are placed in short clusters of 2-3 connected segments
 * instead of single lone posts scattered around the field ("по колышку" looked
 * strange/random). Level 1 stays completely clean (tutorial), and from there
 * obstacle density and cluster variety scale up with the level number.
 */
public class CampaignLevels {

    public static class LevelConfig {
        public final int levelNumber;
        public final int sheepCount;
        public final int fencesCount;
        public final List<Coordinate> wolfSpawns = new ArrayList<>();
        public final List<Coordinate> wolfExits = new ArrayList<>();
        public final List<Coordinate> staticFences = new ArrayList<>();

        public LevelConfig(int levelNumber, int sheepCount, int fencesCount) {
            this.levelNumber = levelNumber;
            this.sheepCount = sheepCount;
            this.fencesCount = fencesCount;
        }

        public LevelConfig addGate(int inX, int inY, int outX, int outY) {
            wolfSpawns.add(new Coordinate(inX, inY));
            wolfExits.add(new Coordinate(outX, outY));
            return this;
        }

        public LevelConfig addFence(int x, int y) {
            staticFences.add(new Coordinate(x, y));
            return this;
        }

        public LevelConfig addWallH(int xStart, int xEnd, int y) {
            for (int x = xStart; x <= xEnd; x++) {
                staticFences.add(new Coordinate(x, y));
            }
            return this;
        }

        public LevelConfig addWallV(int x, int yStart, int yEnd) {
            for (int y = yStart; y <= yEnd; y++) {
                staticFences.add(new Coordinate(x, y));
            }
            return this;
        }

        /**
         * Places a short connected cluster of 2-3 fence posts starting at (x, y),
         * growing horizontally or vertically. This reads as a deliberate obstacle
         * (a little wall/hedge stub) rather than a single random colышек.
         */
        public LevelConfig addCluster(int x, int y, boolean horizontal, int length) {
            length = Math.max(2, Math.min(3, length));
            if (horizontal) {
                return addWallH(x, x + length - 1, y);
            } else {
                return addWallV(x, y, y + length - 1);
            }
        }

        /** Avoids duplicate/overlapping cells when mixing manual + procedural placement. */
        private boolean hasFenceAt(int x, int y) {
            for (Coordinate c : staticFences) {
                if (c.x == x && c.y == y) return true;
            }
            return false;
        }
    }

    public static LevelConfig getLevel(int level) {
        switch (level) {
            case 1:
                // Level 1: Open pasture tutorial - completely clean field, no obstacles.
                return new LevelConfig(1, 1, 8)
                        .addGate(4, 0, 4, 9);

            case 2:
                // Level 2: One small 2-tile obstacle cluster instead of a lone post.
                return new LevelConfig(2, 1, 8)
                        .addGate(5, 0, 5, 9)
                        .addCluster(4, 4, true, 2);

            case 3:
                // Level 3: Central barrier split (3-tile vertical cluster).
                return new LevelConfig(3, 1, 8)
                        .addGate(3, 0, 6, 9)
                        .addWallV(5, 3, 5);

            case 4:
                // Level 4: Corner obstacle (3-tile horizontal cluster).
                return new LevelConfig(4, 1, 8)
                        .addGate(4, 0, 4, 9)
                        .addWallH(3, 5, 4);

            case 5:
                // Level 5: Two wolves, single passage.
                return new LevelConfig(5, 1, 8)
                        .addGate(3, 0, 7, 9)
                        .addGate(0, 4, 9, 4)
                        .addWallH(4, 6, 4);

            case 6:
                // Level 6: Compact corner wall plus a 2-tile cluster (no lone posts).
                return new LevelConfig(6, 1, 8)
                        .addGate(5, 0, 5, 9)
                        .addWallH(4, 6, 5)
                        .addCluster(6, 3, false, 2);

            case 7:
                // Level 7: Two staggered 2-tile clusters instead of three lone posts.
                return new LevelConfig(7, 1, 8)
                        .addGate(4, 0, 4, 9)
                        .addGate(0, 5, 9, 5)
                        .addCluster(3, 3, true, 2)
                        .addCluster(5, 6, true, 2);

            case 8:
                // Level 8: Center cross obstacle (wall + attached 2-tile cluster).
                return new LevelConfig(8, 1, 8)
                        .addGate(3, 0, 6, 9)
                        .addWallH(4, 6, 5)
                        .addCluster(5, 3, false, 2);

            case 9:
                // Level 9: Double entrance squeeze (wall + 2-tile cluster).
                return new LevelConfig(9, 1, 8)
                        .addGate(2, 0, 7, 9)
                        .addGate(7, 0, 2, 9)
                        .addWallH(3, 5, 4)
                        .addCluster(5, 6, true, 2);

            case 10:
                // Level 10: Corridor divider (wall + 2-tile cluster).
                return new LevelConfig(10, 1, 8)
                        .addGate(5, 0, 5, 9)
                        .addGate(0, 3, 9, 6)
                        .addWallV(4, 3, 5)
                        .addCluster(6, 4, false, 2);

            case 11:
                // Level 11: Twin obstacle clusters (3-tile wall + 2-tile cluster).
                return new LevelConfig(11, 1, 8)
                        .addGate(4, 0, 4, 9)
                        .addGate(6, 0, 6, 9)
                        .addWallH(3, 5, 3)
                        .addCluster(5, 6, true, 2);

            case 12:
                // Level 12: Staggered obstacle (wall + adjoining 2-tile cluster).
                return new LevelConfig(12, 1, 8)
                        .addGate(3, 0, 3, 9)
                        .addGate(0, 6, 9, 6)
                        .addWallH(4, 6, 4)
                        .addCluster(4, 5, false, 2);

            case 13:
                // Level 13: Three wolf entrances, one longer wall.
                return new LevelConfig(13, 1, 8)
                        .addGate(3, 0, 3, 9)
                        .addGate(6, 0, 6, 9)
                        .addGate(0, 4, 9, 4)
                        .addWallH(3, 6, 5);

            case 14:
                // Level 14: Central cluster (wall + 2-tile cluster).
                return new LevelConfig(14, 1, 8)
                        .addGate(4, 0, 4, 9)
                        .addGate(0, 5, 9, 5)
                        .addWallH(4, 6, 4)
                        .addCluster(5, 6, false, 2);

            case 15:
                // Level 15: Fortress alcove (wall + 2-tile cluster).
                return new LevelConfig(15, 1, 8)
                        .addGate(5, 0, 5, 9)
                        .addGate(0, 3, 9, 3)
                        .addGate(0, 7, 9, 7)
                        .addWallH(3, 5, 4)
                        .addCluster(6, 5, false, 2);

            case 16:
                // Level 16: First level with 2 sheep! Needs 12 fences for 3 stars.
                return new LevelConfig(16, 2, 12)
                        .addGate(4, 0, 4, 9)
                        .addGate(0, 5, 9, 5)
                        .addWallH(3, 6, 5);

            case 17:
                // Level 17: 2 sheep, split divider.
                return new LevelConfig(17, 2, 12)
                        .addGate(3, 0, 3, 9)
                        .addGate(6, 0, 6, 9)
                        .addWallV(5, 3, 6);

            case 18:
                // Level 18: 2 sheep, natural canyon (wall + 2-tile cluster).
                return new LevelConfig(18, 2, 12)
                        .addGate(4, 0, 4, 9)
                        .addGate(0, 4, 9, 4)
                        .addGate(0, 7, 9, 7)
                        .addWallH(3, 5, 4)
                        .addCluster(6, 5, false, 2);

            case 19:
                // Level 19: 2 sheep, triple entrance assault (wall + 2-tile cluster).
                return new LevelConfig(19, 2, 12)
                        .addGate(3, 0, 3, 9)
                        .addGate(6, 0, 6, 9)
                        .addGate(0, 5, 9, 5)
                        .addWallH(4, 6, 4)
                        .addCluster(5, 6, false, 2);

            case 20:
                // Level 20: 2 sheep, four small corner clusters (2 tiles each).
                return new LevelConfig(20, 2, 12)
                        .addGate(2, 0, 7, 9)
                        .addGate(7, 0, 2, 9)
                        .addCluster(3, 3, true, 2)
                        .addCluster(6, 3, true, 2)
                        .addCluster(3, 6, true, 2)
                        .addCluster(6, 6, true, 2);

            default:
                return generateProceduralLevel(level);
        }
    }

    /**
     * Levels 21-30 (and beyond): procedurally generated using clustered obstacles
     * that get progressively busier with the level number. Deterministic per level
     * (seeded RNG) so a given level always looks/plays the same, but each level
     * feels distinct and the clusters never render as isolated single posts.
     */
    private static LevelConfig generateProceduralLevel(int level) {
        int sCount = 2;
        int fCount = 12;
        Random rng = new Random(1000L + level);
        int clusterCount = Math.min(2 + (level - 21) / 3, 5);
        int minX = 2, maxX = 7, minY = 2, maxY = 7;
        LevelConfig cfg = null;
        for (int retry = 0; retry < 10; retry++) {
            cfg = new LevelConfig(level, sCount, fCount)
                .addGate(3 + (level % 3), 0, 6 - (level % 3), 9)
                .addGate(0, 3 + (level % 4), 9, 6 - (level % 4))
                .addGate(6 - (level % 3), 0, 3 + (level % 3), 9);
            int placed = 0, attempts = 0;
            while (placed < clusterCount && attempts < 40) {
                attempts++;
                boolean horizontal = rng.nextBoolean();
                int length = 2 + rng.nextInt(2);
                int x = minX + rng.nextInt(Math.max(1, maxX - minX - (horizontal ? length : 0)));
                int y = minY + rng.nextInt(Math.max(1, maxY - minY - (horizontal ? 0 : length)));
                if (isClusterFree(cfg, x, y, horizontal, length)) {
                    cfg.addCluster(x, y, horizontal, length);
                    placed++;
                }
            }
            GameState dummyState = new GameState(1, false);
            dummyState.width = 10; dummyState.height = 10;
            for (int i=0; i<10; i++) for(int j=0; j<10; j++) dummyState.grid[i][j] = CellType.EMPTY;
            for (Coordinate c : cfg.staticFences) dummyState.grid[c.x][c.y] = CellType.FENCE;
            dummyState.grid[5][5] = CellType.SHEEP;
            boolean reachable = true;
            for (Coordinate spawn : cfg.wolfSpawns) {
                if (!Pathfinder.canWolfReachSheep(dummyState, dummyState.getWolfLandingCoord(spawn))) {
                    reachable = false; break;
                }
            }
            if (reachable && dummyState.grid[5][5] != CellType.FENCE) break;
        }
        return cfg;
    }
    private static boolean isClusterFree(LevelConfig cfg, int x, int y, boolean horizontal, int length) {
        for (int i = 0; i < length; i++) {
            int cx = horizontal ? x + i : x;
            int cy = horizontal ? y : y + i;
            if (cfg.hasFenceAt(cx, cy)) return false;
            // Keep a 1-tile buffer around existing obstacles so clusters read as
            // separate little walls instead of merging into one blob.
            for (Coordinate c : cfg.staticFences) {
                if (Math.abs(c.x - cx) <= 1 && Math.abs(c.y - cy) <= 1) return false;
            }
        }
        return true;
    }
}
