import java.util.ArrayList;
import java.util.List;

public class ArStyleSolver {
    private static final int[][] PEERS = buildPeers();

    private static int[][] buildPeers() {
        int[][] peers = new int[81][];
        for (int i = 0; i < 81; i++) {
            List<Integer> ps = new ArrayList<>();
            for (int j = 0; j < 81; j++) {
                if (SudokuBoard.sameUnit(i, j)) ps.add(j);
            }
            int[] arr = new int[ps.size()];
            for (int k = 0; k < arr.length; k++) arr[k] = ps.get(k);
            peers[i] = arr;
        }
        return peers;
    }

    public static int[] solve(int[] puzzle) {
        int[] grid = puzzle.clone();
        for (int i = 0; i < 81; i++) {
            if (grid[i] != 0) continue;
            int best = 0;
            int bestScore = -1;
            for (int d = 1; d <= 9; d++) {
                if (!fits(grid, i, d)) continue;
                int score = freedom(grid, i, d);
                if (score > bestScore) {
                    bestScore = score;
                    best = d;
                }
            }
            if (best == 0) return null;
            grid[i] = best;
        }
        return grid;
    }

    private static boolean fits(int[] grid, int i, int d) {
        for (int j : PEERS[i]) {
            if (grid[j] == d) return false;
        }
        return true;
    }

    private static int freedom(int[] grid, int i, int d) {
        grid[i] = d;
        int total = 0;
        for (int j : PEERS[i]) {
            if (grid[j] != 0) continue;
            total += candidates(grid, j);
        }
        grid[i] = 0;
        return total;
    }

    private static int candidates(int[] grid, int j) {
        boolean[] seen = new boolean[10];
        for (int k : PEERS[j]) {
            if (grid[k] != 0) seen[grid[k]] = true;
        }
        int n = 0;
        for (int d = 1; d <= 9; d++) {
            if (!seen[d]) n++;
        }
        return n;
    }
}
