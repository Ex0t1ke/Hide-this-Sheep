package com.example.myapplication;

import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;

public class Pathfinder {

    // 8 directions: 4 orthogonal first, then 4 diagonal
    private static final int[] DX = {0, 0, 1, -1, 1, 1, -1, -1};
    private static final int[] DY = {1, -1, 0, 0, 1, -1, 1, -1};

    /**
     * Checks if a cell is within bounds and traversable (not a fence).
     */
    public static boolean isCellFree(GameState state, int x, int y) {
        if (state == null || state.grid == null) return false;
        if (x < 0 || x >= state.width || y < 0 || y >= state.height) {
            return false;
        }
        return state.grid[x][y] != CellType.FENCE;
    }

    public static boolean isMoveValid(GameState state, int nx, int ny) {
        return isCellFree(state, nx, ny);
    }

    /**
     * Checks if moving from (cx, cy) to (nx, ny) is valid.
     * Wolves move across all 8 directions freely as long as the target cell is traversable.
     * Leaving corners open (e.g. 4-fence plus around a sheep) allows wolves to pass diagonally into the sheep!
     * To fully isolate a sheep, all 8 surrounding cells must be closed.
     */
    public static boolean isMoveValid(GameState state, int cx, int cy, int nx, int ny) {
        return isCellFree(state, nx, ny);
    }

    /**
     * Checks if a wolf can reach any sheep from the spawn coordinate using 8-directional BFS.
     */
    public static boolean canWolfReachSheep(GameState state, Coordinate spawn) {
        if (spawn.x < 0 || spawn.x >= state.width || spawn.y < 0 || spawn.y >= state.height) {
            return false;
        }

        Queue<Coordinate> queue = new LinkedList<>();
        Set<Coordinate> visited = new HashSet<>();

        queue.add(spawn);
        visited.add(spawn);

        while (!queue.isEmpty()) {
            Coordinate curr = queue.poll();

            if (state.grid[curr.x][curr.y] == CellType.SHEEP) {
                return true;
            }

            for (int i = 0; i < 8; i++) {
                int nx = curr.x + DX[i];
                int ny = curr.y + DY[i];

                if (isMoveValid(state, curr.x, curr.y, nx, ny)) {
                    Coordinate next = new Coordinate(nx, ny);
                    if (!visited.contains(next)) {
                        visited.add(next);
                        queue.add(next);
                    }
                }
            }
        }
        return false;
    }

    /**
     * Returns the shortest path from spawn to the closest sheep using 8-directional BFS with parent tracking.
     * Used for wolf walk animation. Returns empty list if no path exists.
     */
    public static List<Coordinate> findPathToSheep(GameState state, Coordinate spawn) {
        if (spawn.x < 0 || spawn.x >= state.width || spawn.y < 0 || spawn.y >= state.height) {
            return new ArrayList<>();
        }

        Queue<Coordinate> queue = new LinkedList<>();
        Map<Coordinate, Coordinate> parent = new HashMap<>();

        queue.add(spawn);
        parent.put(spawn, null);

        Coordinate target = null;

        while (!queue.isEmpty()) {
            Coordinate curr = queue.poll();

            if (state.grid[curr.x][curr.y] == CellType.SHEEP) {
                target = curr;
                break;
            }

            for (int i = 0; i < 8; i++) {
                int nx = curr.x + DX[i];
                int ny = curr.y + DY[i];

                if (isMoveValid(state, curr.x, curr.y, nx, ny)) {
                    Coordinate next = new Coordinate(nx, ny);
                    if (!parent.containsKey(next)) {
                        parent.put(next, curr);
                        queue.add(next);
                    }
                }
            }
        }

        if (target == null) return new ArrayList<>();

        // Reconstruct path from target back to spawn
        List<Coordinate> path = new ArrayList<>();
        Coordinate step = target;
        while (step != null) {
            path.add(step);
            step = parent.get(step);
        }
        Collections.reverse(path);
        return path;
    }

    /**
     * Finds the shortest 8-directional path from a wolf entrance spawn to an exit gate.
     * Used for animation when sheep are safe and wolves cross the pasture to leave.
     * Returns the path from spawn to exit.
     */
    public static List<Coordinate> findPathToExit(GameState state, Coordinate spawn, Coordinate exit) {
        if (spawn == null || spawn.x < 0 || spawn.x >= state.width || spawn.y < 0 || spawn.y >= state.height) {
            return new ArrayList<>();
        }
        if (exit == null || exit.x < 0 || exit.x >= state.width || exit.y < 0 || exit.y >= state.height) {
            return new ArrayList<>();
        }

        Queue<Coordinate> queue = new LinkedList<>();
        Map<Coordinate, Coordinate> parent = new HashMap<>();

        queue.add(spawn);
        parent.put(spawn, null);

        boolean reached = false;
        Coordinate closest = spawn;
        double minDistance = Math.hypot(spawn.x - exit.x, spawn.y - exit.y);

        while (!queue.isEmpty()) {
            Coordinate curr = queue.poll();

            if (curr.x == exit.x && curr.y == exit.y) {
                reached = true;
                break;
            }

            double dist = Math.hypot(curr.x - exit.x, curr.y - exit.y);
            if (dist < minDistance) {
                minDistance = dist;
                closest = curr;
            }

            for (int i = 0; i < 8; i++) {
                int nx = curr.x + DX[i];
                int ny = curr.y + DY[i];

                if (isMoveValid(state, curr.x, curr.y, nx, ny) || (nx == exit.x && ny == exit.y)) {
                    // Wolves do not step onto protected sheep when crossing to exit
                    if (state.grid[nx][ny] == CellType.SHEEP) {
                        continue;
                    }
                    Coordinate next = new Coordinate(nx, ny);
                    if (!parent.containsKey(next)) {
                        parent.put(next, curr);
                        queue.add(next);
                    }
                }
            }
        }

        Coordinate endPoint = reached ? exit : closest;
        List<Coordinate> path = new ArrayList<>();
        Coordinate step = endPoint;
        while (step != null) {
            path.add(step);
            step = parent.get(step);
        }
        Collections.reverse(path);
        return path;
    }
}