# Self-Solving Sudoku — NCM style

A zero-dependency Java implementation of a **self-solving Sudoku** that demonstrates the generation
paradigm from Stanford's **Next-Concept Modeling (NCM)** paper: predict everything at once, then go
back and correct your own output — something sequential, token-by-token ("ChatGPT-style")
generation fundamentally cannot do.

Ships with:

- a **local web UI** — generate a puzzle, play it by hand, or watch the solver fill the board
  level by level (the animation replays the solver's *real* trajectory, just paced for humans)
- an **NCM-style solver** — parallel coarse-to-fine prediction with self-correction (pure Java)
- **Dancing Links** (Knuth's Algorithm X) — microsecond exact-cover ground truth, used as the
  puzzle generator and to verify every single solution
- an **AR-style baseline** — a sequential solver that never revisits its own output, showing why
  autoregressive generation collapses on constraint problems
- a **CLI benchmark** — 300 unique-solution boards across three difficulties

## Quick start

Requires JDK 17+ (tested on 21). No build tools, no dependencies.

```bash
javac -d out src/*.java
java -cp out WebServer     # web UI at http://localhost:8080
java -cp out Main          # CLI self-test + 300-board benchmark
```

Or open the project in IntelliJ and run `WebServer` / `Main` directly.

> Run `WebServer` from the project root so it can find `web/index.html`.

## Web UI

| Control | What it does |
|---|---|
| **Generate puzzle** | easy (40 clues) / medium (35) / hard (30) / expert — Arto Inkala's 2010 21-clue board |
| **Play manually** | click a cell and type 1-9 (arrows move, Backspace erases, Esc deselects) or use the number pad; red tint marks clashes, pad badges count remaining digits, completion is detected and timed |
| **Solve (NCM-style)** | Level 0 fills all 81 cells in parallel (blue), then each level amber-flashes only the cells the solver *rewrites* while the conflict counter drops; ends with a DLX-verified banner |
| **Speed** | Slow / Normal / Fast animation pacing |

## How the NCM-style solver works

The [NCM paper](https://arxiv.org/abs/2610.14162) generates text as a hierarchy of discrete codes,
coarse level to fine level, predicting all positions of a level **in parallel** in a single step,
with finer levels correcting coarser ones. This project ports that paradigm to Sudoku with logical
per-level predictors instead of a trained transformer:

1. **Level 0 — parallel predict.** All 81 cells are produced at once, conditioned on the clues:
   each row is solved as a small assignment problem (bitmask DP over the digits missing from the
   row), scored against column/box conflicts.
2. **Levels — parallel self-correction.** Every row containing an empty or clashing cell is
   re-predicted *simultaneously* against a snapshot of the previous level; rows already consistent
   are left untouched — the solver literally goes back and rewrites only what it got wrong.
3. **Sampling temperature decays per level.** Early levels sample diversely among near-optimal
   row assignments (coarse, uncertain); later levels sharpen toward the optimum.
4. **Stagnation handling.** A few corrupting shuffles escape oscillation; exhausted budgets
   resample the sketch (restarts).

Every solved board is checked against the Dancing Links solution before being reported.

> **Honest framing:** this is a *paradigm* port. The paper's model is a multiscale residual VQ
> tokenizer plus a transformer (checkpoints on HuggingFace, PyTorch/CUDA). No code or weights from
> the official repository were used here — everything in this repo is original Java.

## Ground truth: Dancing Links

`DancingLinks.java` models Sudoku as a 729 × 324 exact-cover matrix and runs Knuth's Algorithm X
with the Dancing Links technique: O(1) unlink/relink backtracking with a min-column heuristic.
It solves any 9×9 board in tens of microseconds, counts solutions (used to guarantee
unique-solution puzzles during generation), and verifies every NCM-style and manual result.

## Benchmark

100 unique-solution boards per difficulty (the paper's 40/35/30-clue setup), all solutions
verified against DLX:

| Difficulty | AR-style (sequential) | NCM-style (this repo) | Paper's NCM | Paper's AR |
|---|---|---|---|---|
| easy (40 clues) | 11.0% | **100%** | 99.5% | 14.7% |
| medium (35 clues) | 4.0% | **100%** | 96.5% | 5.2% |
| hard (30 clues) | 0.0% | **100%** | 86.1% | 1.0% |

DLX solves each board in ~50 µs on average. Inkala's 21-clue board (below the paper's hardest
setting) is also solved and verified. Run `java -cp out Main` to reproduce.

## Project structure

```
src/
  Main.java             CLI self-test + benchmark, level-by-level demo
  WebServer.java        zero-dependency HTTP server (JDK HttpServer) + startup self-test
  NcmStyleSolver.java   coarse-to-fine parallel self-correcting solver
  ArStyleSolver.java    sequential baseline (never revisits its output)
  DancingLinks.java     Knuth Algorithm X exact cover: solver, verifier, puzzle generator
  SudokuBoard.java      board model, parsing, conflict costs, rendering
web/
  index.html            single-file UI: generate / play manually / watch it solve
```

## HTTP API

```bash
curl "http://localhost:8080/api/generate?clues=30"   # easy=40, medium=35, hard=30
curl "http://localhost:8080/api/generate?inkala=true"
curl -X POST --data "<81-char puzzle, digits and .>" http://localhost:8080/api/solve
```

`/api/solve` returns the level trajectory (grid + conflict count per level), level/restart counts,
DLX verification and timing, and whether the AR-style baseline would have solved the board.

## Attribution

- **The study this project demonstrates:** Hermann Kumbong, Ishan S. Khare, Iddah Mlauzi,
  Andrew Woen, Charles Ding, E. Kelly Buchanan, Simran Arora, Azalia Mirhoseini, Christopher Ré.
  *Next-Concept Modeling: Fast, Parallel Coarse-to-Fine Text Generation.*
  arXiv:2610.14162 (2026). https://arxiv.org/abs/2610.14162 —
  [paper](https://arxiv.org/abs/2610.14162) ·
  [blog post](https://hazyresearch.stanford.edu/blog/2026-10-12-ncm) ·
  [official code (MIT)](https://github.com/HazyResearch/ncm).
  The difficulty tiers (40/35/30 clues) and the AR-vs-NCM comparison follow their Sudoku evaluation.
- **Dancing Links / Algorithm X:** Donald E. Knuth. *Dancing Links.*
  arXiv:cs/0011047 (2000). https://arxiv.org/abs/cs/0011047
- **Expert board:** Arto Inkala's 2010 21-clue puzzle, widely reported as the "world's hardest
  sudoku", is included as the expert-level demo board.
- **Further reading:** Peter Norvig. *Solving Every Sudoku Puzzle* (2006).
  https://norvig.com/sudoku.html

This is an independent implementation, not affiliated with or endorsed by the authors. The
paradigm description above paraphrases the paper's public abstract and blog post.

## License

[MIT](LICENSE)
