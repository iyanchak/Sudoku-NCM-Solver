import java.util.Random;

public class DancingLinks {
    private static final int COLS = 324;

    private static class Node {
        Node l, r, u, d;
        Col c;
        int cell;
        int digit;
    }

    private static final class Col extends Node {
        int size;
    }

    private final Col root = new Col();
    private final Col[] cols = new Col[COLS];
    private final java.util.ArrayList<Node> stack = new java.util.ArrayList<>();

    public DancingLinks(int[] puzzle) {
        root.l = root;
        root.r = root;
        root.u = root;
        root.d = root;
        root.c = root;
        for (int i = 0; i < COLS; i++) {
            Col c = new Col();
            c.l = root.l;
            c.r = root;
            root.l.r = c;
            root.l = c;
            c.u = c;
            c.d = c;
            c.size = 0;
            c.c = c;
            cols[i] = c;
        }
        for (int cellIdx = 0; cellIdx < 81; cellIdx++) {
            int given = puzzle[cellIdx];
            for (int d = 1; d <= 9; d++) {
                if (given != 0 && given != d) continue;
                addRow(cellIdx, d);
            }
        }
    }

    private void addRow(int cellIdx, int d) {
        int r = cellIdx / 9, c = cellIdx % 9, b = (r / 3) * 3 + c / 3;
        int[] colIds = {cellIdx, 81 + r * 9 + d - 1, 162 + c * 9 + d - 1, 243 + b * 9 + d - 1};
        Node first = null;
        for (int colId : colIds) {
            Node n = new Node();
            n.cell = cellIdx;
            n.digit = d;
            Col col = cols[colId];
            n.c = col;
            n.u = col.u;
            n.d = col;
            col.u.d = n;
            col.u = n;
            col.size++;
            if (first == null) {
                first = n;
                n.l = n;
                n.r = n;
            } else {
                n.l = first.l;
                n.r = first;
                first.l.r = n;
                first.l = n;
            }
        }
    }

    private void cover(Col c) {
        c.r.l = c.l;
        c.l.r = c.r;
        for (Node i = c.d; i != c; i = i.d) {
            for (Node j = i.r; j != i; j = j.r) {
                j.d.u = j.u;
                j.u.d = j.d;
                j.c.size--;
            }
        }
    }

    private void uncover(Col c) {
        for (Node i = c.u; i != c; i = i.u) {
            for (Node j = i.l; j != i; j = j.l) {
                j.d.u = j;
                j.u.d = j;
                j.c.size++;
            }
        }
        c.r.l = c;
        c.l.r = c;
    }

    private int search(int limit, int[] outGrid) {
        if (root.r == root) {
            if (outGrid != null) {
                for (Node n : stack) outGrid[n.cell] = n.digit;
            }
            return 1;
        }
        Col best = null;
        for (Node n = root.r; n != root; n = n.r) {
            Col c = (Col) n;
            if (best == null || c.size < best.size) best = c;
        }
        if (best.size == 0) return 0;
        int found = 0;
        cover(best);
        for (Node row = best.d; row != best && found < limit; row = row.d) {
            stack.add(row);
            for (Node j = row.r; j != row; j = j.r) cover(j.c);
            found += search(limit - found, outGrid);
            for (Node j = row.l; j != row; j = j.l) uncover(j.c);
            stack.remove(stack.size() - 1);
            if (found >= limit) break;
        }
        uncover(best);
        return found;
    }

    public int[] solve() {
        int[] grid = new int[81];
        return search(1, grid) == 1 ? grid : null;
    }

    public int countSolutions(int limit) {
        return search(limit, null);
    }

    public static int[] solve(int[] puzzle) {
        return new DancingLinks(puzzle).solve();
    }

    public static int countSolutions(int[] puzzle, int limit) {
        return new DancingLinks(puzzle).countSolutions(limit);
    }

    public static int[] randomFullGrid(Random rng) {
        while (true) {
            int[] grid = new int[81];
            if (fill(grid, 0, rng)) return grid;
        }
    }

    private static boolean fill(int[] grid, int i, Random rng) {
        if (i == 81) return true;
        if (grid[i] != 0) return fill(grid, i + 1, rng);
        int[] ds = {1, 2, 3, 4, 5, 6, 7, 8, 9};
        for (int k = 9; k > 1; k--) {
            int p = rng.nextInt(k);
            int t = ds[k - 1];
            ds[k - 1] = ds[p];
            ds[p] = t;
        }
        for (int d : ds) {
            if (fits(grid, i, d)) {
                grid[i] = d;
                if (fill(grid, i + 1, rng)) return true;
                grid[i] = 0;
            }
        }
        return false;
    }

    private static boolean fits(int[] grid, int i, int d) {
        for (int j = 0; j < 81; j++) {
            if (j != i && grid[j] == d && SudokuBoard.sameUnit(i, j)) return false;
        }
        return true;
    }

    public static int[] generatePuzzle(int clues, Random rng) {
        while (true) {
            int[] puzzle = randomFullGrid(rng);
            int[] order = new int[81];
            for (int i = 0; i < 81; i++) order[i] = i;
            for (int k = 81; k > 1; k--) {
                int p = rng.nextInt(k);
                int t = order[k - 1];
                order[k - 1] = order[p];
                order[p] = t;
            }
            int remaining = 81;
            int idx = 0;
            while (remaining > clues) {
                boolean removed = false;
                while (idx < 81) {
                    int cell = order[idx++];
                    if (puzzle[cell] == 0) continue;
                    int backup = puzzle[cell];
                    puzzle[cell] = 0;
                    if (countSolutions(puzzle, 2) == 1) {
                        remaining--;
                        removed = true;
                        break;
                    }
                    puzzle[cell] = backup;
                }
                if (!removed) break;
            }
            if (remaining <= clues) return puzzle;
        }
    }
}
