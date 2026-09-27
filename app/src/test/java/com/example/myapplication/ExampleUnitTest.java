package com.example.myapplication;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class ExampleUnitTest {

    @Test
    public void testInventoryFencesBetweenSevenAndTen() {
        // Across all levels 1 to 30, inventory fences must be between 7 and 10
        for (int level = 1; level <= 30; level++) {
            GameState state = new GameState(level, false);
            assertTrue("Inventory fences must not exceed 10 at level " + level, state.availableFences <= 10);
            assertTrue("Inventory fences must be at least 7 at level " + level, state.availableFences >= 7);
            assertEquals(state.availableFences, state.initialFences);
        }

        // Across endless waves
        for (int wave = 1; wave <= 50; wave++) {
            GameState state = new GameState(wave, true);
            assertTrue("Inventory fences must not exceed 10 at wave " + wave, state.availableFences <= 10);
            assertTrue("Inventory fences must be at least 7 at wave " + wave, state.availableFences >= 7);
        }
    }

    @Test
    public void testWolfGatesSpawnOnPerimeterBorders() {
        for (int level = 1; level <= 30; level++) {
            GameState state = new GameState(level, false);
            assertFalse("Wolf spawns must not be empty", state.wolfSpawns.isEmpty());
            for (Coordinate spawn : state.wolfSpawns) {
                // Must be on one of the 4 outer walls
                boolean onBorder = (spawn.x == 0 || spawn.x == state.width - 1
                        || spawn.y == 0 || spawn.y == state.height - 1);
                assertTrue("Gate must spawn on perimeter border at level " + level + ": " + spawn, onBorder);
                assertEquals("Perimeter fence must remain solid without holes", CellType.FENCE, state.grid[spawn.x][spawn.y]);

                Coordinate landing = state.getWolfLandingCoord(spawn);
                assertNotNull("Landing coordinate must not be null", landing);
                assertTrue("Landing must be inside field", landing.x >= 1 && landing.x <= state.width - 2
                        && landing.y >= 1 && landing.y <= state.height - 2);
            }
        }
    }

    @Test
    public void testFourFencesLeaveCornersOpenAllowingWolfToReachSheep() {
        GameState state = new GameState(1, false);
        for (int x = 1; x < state.width - 1; x++) {
            for (int y = 1; y < state.height - 1; y++) {
                state.grid[x][y] = CellType.EMPTY;
            }
        }
        // Sheep at (5, 4)
        state.grid[5][4] = CellType.SHEEP;

        // Player places only 4 fences (North, South, West, East)
        state.grid[5][3] = CellType.FENCE; // North
        state.grid[5][5] = CellType.FENCE; // South
        state.grid[4][4] = CellType.FENCE; // West
        state.grid[6][4] = CellType.FENCE; // East

        // Corners (4, 3), (6, 3), (4, 5), (6, 5) are OPEN
        Coordinate spawn = state.wolfSpawns.get(0);
        Coordinate landing = state.getWolfLandingCoord(spawn);
        boolean canReach = Pathfinder.canWolfReachSheep(state, landing);

        // Wolf must be able to reach sheep through the open diagonal corners
        assertTrue("Wolf can infiltrate through open diagonal corners when only 4 fences are placed", canReach);
    }

    @Test
    public void testDiagonalMovementIntoFreeCell() {
        GameState state = new GameState(1, false);
        for (int x = 1; x < state.width - 1; x++) {
            for (int y = 1; y < state.height - 1; y++) {
                state.grid[x][y] = CellType.EMPTY;
            }
        }
        // From (4, 3) to (5, 4) with no fence at target is valid
        assertTrue("Wolf can move diagonally into a free cell", Pathfinder.isMoveValid(state, 4, 3, 5, 4));

        // Moving into a fence is blocked
        state.grid[5][4] = CellType.FENCE;
        assertFalse("Wolf cannot step into a fence", Pathfinder.isMoveValid(state, 4, 3, 5, 4));
    }

    @Test
    public void testFullEnclosureProtectsSheep() {
        GameState state = new GameState(1, false);
        for (int x = 1; x < state.width - 1; x++) {
            for (int y = 1; y < state.height - 1; y++) {
                state.grid[x][y] = CellType.EMPTY;
            }
        }
        state.grid[5][4] = CellType.SHEEP;
        state.grid[4][3] = CellType.FENCE;
        state.grid[5][3] = CellType.FENCE;
        state.grid[6][3] = CellType.FENCE;
        state.grid[4][4] = CellType.FENCE;
        state.grid[6][4] = CellType.FENCE;
        state.grid[4][5] = CellType.FENCE;
        state.grid[5][5] = CellType.FENCE;
        state.grid[6][5] = CellType.FENCE;

        Coordinate spawn = state.wolfSpawns.get(0);
        Coordinate landing = state.getWolfLandingCoord(spawn);
        boolean canReach = Pathfinder.canWolfReachSheep(state, landing);
        assertFalse("Wolf cannot penetrate full 8-cell enclosure", canReach);
    }

    @Test
    public void testOpenCorridorAllowsWolfToReachSheep() {
        GameState state = new GameState(1, false);
        for (int x = 1; x < state.width - 1; x++) {
            for (int y = 1; y < state.height - 1; y++) {
                state.grid[x][y] = CellType.EMPTY;
            }
        }
        state.grid[5][4] = CellType.SHEEP;
        // Partially enclosed (open on South)
        state.grid[4][3] = CellType.FENCE;
        state.grid[5][3] = CellType.FENCE;
        state.grid[6][3] = CellType.FENCE;
        state.grid[4][4] = CellType.FENCE;
        state.grid[6][4] = CellType.FENCE;

        Coordinate spawn = state.wolfSpawns.get(0);
        Coordinate landing = state.getWolfLandingCoord(spawn);
        boolean canReach = Pathfinder.canWolfReachSheep(state, landing);
        assertTrue("Wolf reaches sheep through open side", canReach);
    }

    @Test
    public void testCornersHaveNoBoulders() {
        GameState state = new GameState(1, false);
        // Inner buffer tiles are empty pasture
        assertEquals("Corner (1, 1) must be empty", CellType.EMPTY, state.grid[1][1]);
        assertEquals("Corner (8, 1) must be empty", CellType.EMPTY, state.grid[state.width - 2][1]);
        assertEquals("Corner (1, 8) must be empty", CellType.EMPTY, state.grid[1][state.height - 2]);
        assertEquals("Corner (8, 8) must be empty", CellType.EMPTY, state.grid[state.width - 2][state.height - 2]);
    }

    @Test
    public void testLevel1HasCleanFieldAndEightFences() {
        GameState state = new GameState(1, false);
        assertEquals(8, state.availableFences);

        int innerFences = 0;
        for (int x = 2; x <= 7; x++) {
            for (int y = 2; y <= 7; y++) {
                if (state.grid[x][y] == CellType.FENCE) innerFences++;
            }
        }
        assertEquals("Level 1 field should be clean", 0, innerFences);
    }

    @Test
    public void testFieldObstaclesAreSmall() {
        for (int level = 2; level <= 30; level++) {
            GameState state = new GameState(level, false);
            int innerFences = 0;
            for (int x = 2; x <= 7; x++) {
                for (int y = 2; y <= 7; y++) {
                    if (state.grid[x][y] == CellType.FENCE) innerFences++;
                }
            }
            assertTrue("Obstacles must be small (at most 4 fences) at level " + level, innerFences <= 4);
        }
    }

    @Test
    public void testWolfExitsSpawnOnOppositeBorders() {
        for (int level = 1; level <= 30; level++) {
            GameState state = new GameState(level, false);
            assertFalse("Wolf spawns must not be empty", state.wolfSpawns.isEmpty());
            assertEquals("Spawns and exits must match in count", state.wolfSpawns.size(), state.wolfExits.size());

            for (int i = 0; i < state.wolfSpawns.size(); i++) {
                Coordinate spawn = state.wolfSpawns.get(i);
                Coordinate exit = state.wolfExits.get(i);

                assertEquals("Perimeter spawn must be solid fence", CellType.FENCE, state.grid[spawn.x][spawn.y]);
                assertEquals("Perimeter exit must be solid fence", CellType.FENCE, state.grid[exit.x][exit.y]);

                // Verify exit is on opposite wall
                if (spawn.y == 0) assertEquals("Exit must be at bottom if entrance is at top", state.height - 1, exit.y);
                else if (spawn.y == state.height - 1) assertEquals("Exit must be at top if entrance is at bottom", 0, exit.y);
                else if (spawn.x == 0) assertEquals("Exit must be at right if entrance is at left", state.width - 1, exit.x);
                else if (spawn.x == state.width - 1) assertEquals("Exit must be at left if entrance is at right", 0, exit.x);
            }
        }
    }

    @Test
    public void testFindPathToExit() {
        GameState state = new GameState(1, false);
        Coordinate spawn = state.wolfSpawns.get(0);
        Coordinate exit = state.wolfExits.get(0);

        Coordinate landing = state.getWolfLandingCoord(spawn);
        Coordinate exitLanding = state.getWolfLandingCoord(exit);

        List<Coordinate> path = Pathfinder.findPathToExit(state, landing, exitLanding);
        assertFalse("Path from landing to exit landing must not be empty", path.isEmpty());
        assertEquals("Path must start at landing", landing, path.get(0));
        assertEquals("Path must end at exitLanding", exitLanding, path.get(path.size() - 1));

        // Ensure no path step is a fence
        for (Coordinate step : path) {
            assertNotEquals("Path cannot traverse a fence", CellType.FENCE, state.grid[step.x][step.y]);
        }
    }

    @Test
    public void testInnerPastureBoundaryCoordinates() {
        // Inner pasture is [2..7] x [2..7] on 10x10 board
        int width = 10;
        int height = 10;

        // Border tiles that must NOT be in the inner pasture
        int[][] borderTiles = {{1, 1}, {1, 5}, {8, 2}, {8, 8}, {0, 0}, {9, 9}, {5, 1}, {5, 8}};
        for (int[] tile : borderTiles) {
            int x = tile[0], y = tile[1];
            boolean isInner = (x >= 2 && x <= width - 3 && y >= 2 && y <= height - 3);
            assertFalse("Tile (" + x + "," + y + ") must be outside inner pasture", isInner);
        }

        // Inner pasture tiles that must be allowed
        int[][] innerTiles = {{2, 2}, {5, 5}, {7, 7}, {2, 7}, {7, 2}, {4, 3}};
        for (int[] tile : innerTiles) {
            int x = tile[0], y = tile[1];
            boolean isInner = (x >= 2 && x <= width - 3 && y >= 2 && y <= height - 3);
            assertTrue("Tile (" + x + "," + y + ") must be inside inner pasture", isInner);
        }
    }

    @Test
    public void testScoringAndStars() {
        GameState state = new GameState(1, false);
        state.initialFences = 4;
        state.availableFences = 3;
        state.usedFences = 1;

        int score = state.calculateScore();
        assertTrue("Score should be greater than 0", score > 100);

        int stars = state.getStarRating();
        assertEquals("1 used out of 4 is <= 50%, should give 3 stars", 3, stars);
    }
}