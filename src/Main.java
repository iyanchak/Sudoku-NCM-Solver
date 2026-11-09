import java.util.Arrays;
import java.util.Random;

public class Main {
    static final int BOARDS = 100;
    static final long SEED = 42;

    record Demo(int[] puzzle, NcmStyleSolver.Result result) {}

    public static void main(String[] args) {
        System.out.println("====================================================================");
        System.out.println(" Self-solving Sudoku - NCM-style (Next-Concept Modeling paradigm)");
        System.out.println(" Java port of the coarse-to-fine, parallel, self-correcting idea");
        System.out.println(" from arXiv:2610.14162 (Kumbong, Khare et al., Stanford, 2026)");
        System.out.println("====================================================================");
        System.out.println();

        System.out.println("Generating " + (BOARDS * 3) + " unique-solution puzzles (40/35/30 clues)...");
        Random rng = new Random(SEED);
        int[][] easy = dataset(40, rng);
        int[][] medium = dataset(35, rng);
        int[][] hard = dataset(30, rng);
        System.out.println("done.");
        System.out.println();

        demonstrate(findCleanDemo(hard), "Coarse-to-fine demo (hard board, 30 clues)");
        System.out.println();

        System.out.println("--------------------------------------------------------------------");
        System.out.println(" Benchmark: " + BOARDS + " boards per difficulty");
        System.out.println("--------------------------------------------------------------------");
        System.out.printf("%-8s %5s %9s %10s %11s %10s %9s %10s%n",
                "level", "clues", "AR-style", "NCM-style", "avgLevels", "maxLevels", "verified", "DLX avg");
        benchmark("easy", 40, easy);
        benchmark("medium", 35, medium);
        benchmark("hard", 30, hard);
        System.out.println();
        System.out.println("Paper's reference numbers (trained neural NCM vs AR):");
        System.out.println("  easy 99.5% vs 14.7%   medium 96.5% vs 5.2%   hard 86.1% vs 1.0%");
        System.out.println();

        String inkala = String.join("", new String[]{
                "8........",
                "..36.....",
                ".7..9.2..",
                ".5...7...",
                "....457..",
                "...1...3.",
                "..1....68",
                "..85...1.",
                ".9....4.."
        });
        int[] inkalaPuzzle = SudokuBoard.parse(inkala).cells();
        NcmStyleSolver.Result inkalaResult = new NcmStyleSolver(new Random(SEED)).solve(inkalaPuzzle);
        demonstrate(new Demo(inkalaPuzzle, inkalaResult),
                "Bonus: Inkala 2010 \"world's hardest sudoku\" (21 clues)");
    }

    static int[][] dataset(int clues, Random rng) {
        int[][] puzzles = new int[BOARDS][];
        for (int i = 0; i < BOARDS; i++) {
            puzzles[i] = DancingLinks.generatePuzzle(clues, rng);
        }
        return puzzles;
    }

    static Demo findCleanDemo(int[][] puzzles) {
        Demo fallback = null;
        for (int[] p : puzzles) {
            NcmStyleSolver.Result r = new NcmStyleSolver(new Random(SEED)).solve(p);
            if (!r.solved()) continue;
            if (r.restarts == 0 && r.levels <= 30) return new Demo(p, r);
            if (fallback == null) fallback = new Demo(p, r);
        }
        if (fallback != null) return fallback;
        return new Demo(puzzles[0], new NcmStyleSolver(new Random(SEED)).solve(puzzles[0]));
    }

    static void demonstrate(Demo demo, String title) {
        System.out.println("--------------------------------------------------------------------");
        System.out.println(" " + title);
        System.out.println("--------------------------------------------------------------------");
        int[] puzzle = demo.puzzle();
        NcmStyleSolver.Result res = demo.result();
        System.out.println("Puzzle (clues plain, model-written cells in parentheses):");
        System.out.print(new SudokuBoard(puzzle).format(puzzle));
        if (!res.solved()) {
            System.out.println("NCM-style did not solve this board within budget.");
            return;
        }
        int n = res.trajectory.size();
        int[] picks = {0, n / 3, 2 * n / 3, n - 1};
        picks = Arrays.stream(picks).distinct().sorted().toArray();
        for (int k : picks) {
            int[] state = res.trajectory.get(k);
            int conflicts = SudokuBoard.conflicts(state);
            String label = k == 0
                    ? "parallel predict, all 81 cells at once (conditioned on clues)"
                    : "parallel self-correction sweep";
            System.out.printf("%nLevel %d - %s - %d conflicts%n", k, label, conflicts);
            System.out.print(new SudokuBoard(state).format(puzzle));
        }
        long t0 = System.nanoTime();
        int[] truth = DancingLinks.solve(puzzle);
        long dlxNanos = System.nanoTime() - t0;
        boolean verified = truth != null && Arrays.equals(truth, res.grid);
        System.out.printf("%nSolved in %d levels (%d restarts). DLX ground-truth match: %s (DLX took %.1f us)%n",
                res.levels, res.restarts, verified, dlxNanos / 1000.0);
    }

    static void benchmark(String name, int clues, int[][] puzzles) {
        int arSolved = 0;
        int ncmSolved = 0;
        int ncmVerified = 0;
        long totalLevels = 0;
        long maxLevels = 0;
        long dlxNanos = 0;
        NcmStyleSolver ncm = new NcmStyleSolver(new Random(SEED));
        for (int[] p : puzzles) {
            int[] ar = ArStyleSolver.solve(p);
            if (ar != null && SudokuBoard.conflicts(ar) == 0) arSolved++;
            NcmStyleSolver.Result r = ncm.solve(p);
            if (r.solved()) {
                ncmSolved++;
                totalLevels += r.levels;
                maxLevels = Math.max(maxLevels, r.levels);
                int[] truth = DancingLinks.solve(p);
                if (truth != null && Arrays.equals(truth, r.grid)) ncmVerified++;
            }
            long t0 = System.nanoTime();
            DancingLinks.solve(p);
            dlxNanos += System.nanoTime() - t0;
        }
        System.out.printf("%-8s %5d %8.1f%% %9.1f%% %11.1f %10d %8d%% %7.1f us%n",
                name, clues,
                100.0 * arSolved / puzzles.length,
                100.0 * ncmSolved / puzzles.length,
                (double) totalLevels / Math.max(1, ncmSolved),
                maxLevels,
                100 * ncmVerified / Math.max(1, ncmSolved),
                (double) dlxNanos / puzzles.length / 1000.0);
    }
}
