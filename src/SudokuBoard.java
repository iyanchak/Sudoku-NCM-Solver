public class SudokuBoard {
    public static final int CELLS = 81;

    private final int[] cells;

    public SudokuBoard() {
        this.cells = new int[CELLS];
    }

    public SudokuBoard(int[] cells) {
        this.cells = cells.clone();
    }

    public static SudokuBoard parse(String s) {
        int[] vals = new int[CELLS];
        int n = 0;
        for (char c : s.toCharArray()) {
            if (c >= '1' && c <= '9') vals[n++] = c - '0';
            else if (c == '0' || c == '.') vals[n++] = 0;
            if (n > CELLS) break;
        }
        if (n != CELLS) throw new IllegalArgumentException("expected 81 cells, got " + n);
        return new SudokuBoard(vals);
    }

    public int[] cells() {
        return cells.clone();
    }

    public int get(int i) {
        return cells[i];
    }

    public void set(int i, int d) {
        cells[i] = d;
    }

    public static int rowOf(int i) {
        return i / 9;
    }

    public static int colOf(int i) {
        return i % 9;
    }

    public static int boxOf(int i) {
        return (i / 27) * 3 + (i % 9) / 3;
    }

    public static boolean sameUnit(int a, int b) {
        if (a == b) return false;
        return rowOf(a) == rowOf(b) || colOf(a) == colOf(b) || boxOf(a) == boxOf(b);
    }

    public static int unitCell(int unit, int pos) {
        if (unit < 9) return unit * 9 + pos;
        if (unit < 18) return pos * 9 + (unit - 9);
        int br = (unit - 18) / 3, bc = (unit - 18) % 3;
        return (br * 3 + pos / 3) * 9 + bc * 3 + pos % 3;
    }

    public static int unitCost(int[] cells, int unit) {
        int[] cnt = new int[10];
        int cost = 0;
        for (int p = 0; p < 9; p++) {
            int d = cells[unitCell(unit, p)];
            if (d != 0 && ++cnt[d] > 1) cost++;
        }
        return cost;
    }

    public static int conflicts(int[] cells) {
        int total = 0;
        for (int u = 0; u < 27; u++) total += unitCost(cells, u);
        return total;
    }

    public int unitCost(int unit) {
        return unitCost(cells, unit);
    }

    public int conflicts() {
        return conflicts(cells);
    }

    public boolean complete() {
        for (int d : cells) if (d == 0) return false;
        return true;
    }

    public boolean solved() {
        return complete() && conflicts() == 0;
    }

    public boolean matchesClues(int[] puzzle) {
        for (int i = 0; i < CELLS; i++) {
            if (puzzle[i] != 0 && cells[i] != puzzle[i]) return false;
        }
        return true;
    }

    public String toCompactString() {
        StringBuilder sb = new StringBuilder(CELLS);
        for (int d : cells) sb.append(d == 0 ? '.' : (char) ('0' + d));
        return sb.toString();
    }

    @Override
    public String toString() {
        return format(null);
    }

    public String format(int[] puzzle) {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < 9; r++) {
            if (r == 3 || r == 6) sb.append("------+-------+------\n");
            for (int c = 0; c < 9; c++) {
                if (c == 3 || c == 6) sb.append("| ");
                int i = r * 9 + c;
                if (cells[i] == 0) {
                    sb.append(". ");
                } else if (puzzle != null && puzzle[i] != 0) {
                    sb.append(cells[i]).append(' ');
                } else {
                    sb.append('(').append(cells[i]).append(") ");
                }
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
