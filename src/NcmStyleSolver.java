import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class NcmStyleSolver {
    public static class Result {
        public final int[] grid;
        public final int levels;
        public final int restarts;
        public final List<int[]> trajectory;

        Result(int[] grid, int levels, int restarts, List<int[]> trajectory) {
            this.grid = grid;
            this.levels = levels;
            this.restarts = restarts;
            this.trajectory = trajectory;
        }

        public boolean solved() {
            return grid != null;
        }
    }

    private static final int INF = Integer.MAX_VALUE / 4;

    private final Random rng;
    private final int maxLevels;
    private final int maxRestarts;
    private final int patience;
    private final double startTemp;
    private final double tempDecay;
    private final double minTemp;

    public NcmStyleSolver(Random rng) {
        this(rng, 60, 400, 5, 1.0, 0.95, 0.05);
    }

    public NcmStyleSolver(Random rng, int maxLevels, int maxRestarts, int patience) {
        this(rng, maxLevels, maxRestarts, patience, 1.0, 0.95, 0.05);
    }

    public NcmStyleSolver(Random rng, int maxLevels, int maxRestarts, int patience,
                          double startTemp, double tempDecay, double minTemp) {
        this.rng = rng;
        this.maxLevels = maxLevels;
        this.maxRestarts = maxRestarts;
        this.patience = patience;
        this.startTemp = startTemp;
        this.tempDecay = tempDecay;
        this.minTemp = minTemp;
    }

    public Result solve(int[] puzzle) {
        boolean[] fixed = new boolean[81];
        for (int i = 0; i < 81; i++) fixed[i] = puzzle[i] != 0;
        List<int[]> trajectory = new ArrayList<>();
        for (int attempt = 0; attempt <= maxRestarts; attempt++) {
            if (attempt > 0) trajectory.clear();
            int[] grid;
            if (attempt == 0) {
                grid = puzzle.clone();
                predictLevel(grid, grid.clone(), fixed, startTemp);
            } else {
                grid = randomSketch(puzzle);
            }
            trajectory.add(grid.clone());
            int levels = 0;
            int stagnation = 0;
            int prev = SudokuBoard.conflicts(grid);
            if (prev == 0) {
                return new Result(grid, 0, attempt, trajectory);
            }
            while (prev > 0 && levels < maxLevels) {
                double temp = Math.max(minTemp, startTemp * Math.pow(tempDecay, levels));
                predictLevel(grid, grid.clone(), fixed, temp);
                levels++;
                int cost = SudokuBoard.conflicts(grid);
                if (cost == 0) {
                    trajectory.add(grid.clone());
                    return new Result(grid, levels, attempt, trajectory);
                }
                if (cost < prev) stagnation = 0;
                else stagnation++;
                prev = cost;
                if (stagnation >= patience) {
                    perturb(grid, fixed);
                    prev = SudokuBoard.conflicts(grid);
                    stagnation = 0;
                }
                trajectory.add(grid.clone());
            }
        }
        return new Result(null, -1, maxRestarts, trajectory);
    }

    private void predictLevel(int[] grid, int[] snapshot, boolean[] fixed, double temp) {
        int[][] colCnt = new int[9][10];
        int[][] boxCnt = new int[9][10];
        for (int i = 0; i < 81; i++) {
            int d = snapshot[i];
            if (d != 0) {
                colCnt[i % 9][d]++;
                boxCnt[SudokuBoard.boxOf(i)][d]++;
            }
        }
        for (int r = 0; r < 9; r++) {
            boolean dirty = false;
            for (int c = 0; c < 9; c++) {
                int i = r * 9 + c;
                if (!fixed[i] && (grid[i] == 0 || conflictedCell(grid, i))) {
                    dirty = true;
                    break;
                }
            }
            if (dirty) reassignRow(grid, snapshot, colCnt, boxCnt, r, fixed, temp);
        }
    }

    private static boolean conflictedCell(int[] grid, int i) {
        int d = grid[i];
        int c = i % 9;
        int b = SudokuBoard.boxOf(i);
        for (int k = 0; k < 9; k++) {
            int j = (i / 9) * 9 + k;
            if (j != i && grid[j] == d) return true;
            j = k * 9 + c;
            if (j != i && grid[j] == d) return true;
            j = SudokuBoard.unitCell(18 + b, k);
            if (j != i && grid[j] == d) return true;
        }
        return false;
    }

    private void reassignRow(int[] grid, int[] snapshot, int[][] colCnt, int[][] boxCnt, int r, boolean[] fixed, double temp) {
        List<Integer> movable = new ArrayList<>();
        int clueMask = 0;
        for (int c = 0; c < 9; c++) {
            int i = r * 9 + c;
            if (fixed[i]) clueMask |= 1 << snapshot[i];
            else movable.add(i);
        }
        int m = movable.size();
        if (m == 0) return;
        Collections.shuffle(movable, rng);
        int availMask = 0;
        for (int d = 1; d <= 9; d++) {
            if ((clueMask & (1 << d)) == 0) availMask |= 1 << d;
        }
        if (Integer.bitCount(availMask) != m) return;

        int[][] ownCol = new int[9][10];
        int[][] ownBox = new int[3][10];
        for (int c = 0; c < 9; c++) {
            int d = snapshot[r * 9 + c];
            if (d != 0) {
                ownCol[c][d]++;
                ownBox[c / 3][d]++;
            }
        }
        int[][] cost = new int[m][10];
        for (int k = 0; k < m; k++) {
            int i = movable.get(k);
            int c = i % 9;
            int b = SudokuBoard.boxOf(i);
            for (int d = 1; d <= 9; d++) {
                if ((availMask & (1 << d)) == 0) continue;
                cost[k][d] = colCnt[c][d] - ownCol[c][d] + boxCnt[b][d] - ownBox[c / 3][d];
            }
        }

        int[][] go = new int[m + 1][1024];
        for (int[] row : go) Arrays.fill(row, INF);
        go[m][0] = 0;
        for (int k = m - 1; k >= 0; k--) {
            for (int mask = 0; mask < 1024; mask++) {
                if ((mask & ~availMask) != 0 || Integer.bitCount(mask) != m - k) continue;
                int best = INF;
                for (int d = 1; d <= 9; d++) {
                    if ((mask & (1 << d)) == 0) continue;
                    int rest = go[k + 1][mask ^ (1 << d)];
                    if (rest == INF) continue;
                    int v = cost[k][d] + rest;
                    if (v < best) best = v;
                }
                go[k][mask] = best;
            }
        }
        if (go[0][availMask] == INF) return;

        double[] w = new double[10];
        int mask = availMask;
        for (int k = 0; k < m; k++) {
            double total = 0;
            int base = go[k][mask];
            for (int d = 1; d <= 9; d++) {
                if ((mask & (1 << d)) == 0) continue;
                w[d] = Math.exp(-(cost[k][d] + go[k + 1][mask ^ (1 << d)] - base) / temp);
                total += w[d];
            }
            double u = rng.nextDouble() * total;
            int pick = -1;
            double acc = 0;
            for (int d = 1; d <= 9; d++) {
                if ((mask & (1 << d)) == 0) continue;
                acc += w[d];
                if (u <= acc) {
                    pick = d;
                    break;
                }
            }
            if (pick < 0) {
                for (int d = 9; d >= 1; d--) {
                    if ((mask & (1 << d)) != 0) {
                        pick = d;
                        break;
                    }
                }
            }
            grid[movable.get(k)] = pick;
            mask ^= 1 << pick;
        }
    }

    private int[] randomSketch(int[] puzzle) {
        int[] grid = puzzle.clone();
        for (int r = 0; r < 9; r++) {
            List<Integer> empty = new ArrayList<>();
            List<Integer> missing = new ArrayList<>();
            boolean[] present = new boolean[10];
            for (int c = 0; c < 9; c++) {
                int i = r * 9 + c;
                if (puzzle[i] != 0) present[puzzle[i]] = true;
                else empty.add(i);
            }
            for (int d = 1; d <= 9; d++) if (!present[d]) missing.add(d);
            Collections.shuffle(missing, rng);
            for (int k = 0; k < empty.size() && k < missing.size(); k++) {
                grid[empty.get(k)] = missing.get(k);
            }
        }
        return grid;
    }

    private void perturb(int[] grid, boolean[] fixed) {
        for (int t = 0; t < 3; t++) {
            int r = rng.nextInt(9);
            List<Integer> movable = new ArrayList<>();
            for (int c = 0; c < 9; c++) {
                if (!fixed[r * 9 + c]) movable.add(r * 9 + c);
            }
            if (movable.size() < 2) continue;
            List<Integer> ds = new ArrayList<>();
            for (int i : movable) ds.add(grid[i]);
            Collections.shuffle(ds, rng);
            for (int k = 0; k < movable.size(); k++) grid[movable.get(k)] = ds.get(k);
        }
    }
}
