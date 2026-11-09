import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;

public class WebServer {
    private static final String INKALA = String.join("", new String[]{
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

    private static byte[] pageBytes;

    public static void main(String[] args) throws IOException {
        int preferred = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        selfTest();
        HttpServer server = null;
        int port = preferred;
        for (int p = preferred; p < preferred + 10 && server == null; p++) {
            try {
                server = HttpServer.create(new InetSocketAddress(p), 0);
                port = p;
            } catch (IOException e) {
                System.out.println("Port " + p + " busy, trying next...");
            }
        }
        if (server == null) {
            throw new IOException("no free port in " + preferred + ".." + (preferred + 9));
        }
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.createContext("/", WebServer::handleIndex);
        server.createContext("/api/generate", WebServer::handleGenerate);
        server.createContext("/api/solve", WebServer::handleSolve);
        server.start();
        System.out.println();
        System.out.println("  NCM Sudoku server running:");
        System.out.println("  ->  http://localhost:" + port);
        System.out.println("  (Ctrl+C to stop)");
    }

    private static void selfTest() {
        System.out.print("Self-test (generate + solve + DLX verify)... ");
        int[] puzzle = DancingLinks.generatePuzzle(30, new Random(42));
        NcmStyleSolver.Result r = new NcmStyleSolver(new Random(42)).solve(puzzle);
        int[] truth = DancingLinks.solve(puzzle);
        boolean ok = r.solved() && truth != null && Arrays.equals(truth, r.grid);
        System.out.println(ok ? "PASS" : "FAIL");
    }

    private static void handleIndex(HttpExchange ex) throws IOException {
        try {
            if (!ex.getRequestURI().getPath().equals("/")) {
                send(ex, 404, "text/plain; charset=utf-8", "not found");
                return;
            }
            byte[] page = indexPage();
            ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            ex.sendResponseHeaders(200, page.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(page);
            }
        } finally {
            ex.close();
        }
    }

    private static void handleGenerate(HttpExchange ex) throws IOException {
        try {
            Map<String, String> q = query(ex);
            String json;
            if (q.containsKey("inkala")) {
                json = "{\"puzzle\":\"" + INKALA + "\",\"clues\":21,\"name\":\"Inkala 2010 (21 clues)\"}";
            } else {
                int clues = 30;
                try {
                    clues = Integer.parseInt(q.getOrDefault("clues", "30"));
                } catch (NumberFormatException ignored) {
                }
                clues = Math.max(26, Math.min(60, clues));
                long t0 = System.nanoTime();
                int[] puzzle = DancingLinks.generatePuzzle(clues, new Random());
                double ms = (System.nanoTime() - t0) / 1e6;
                String name = clues >= 38 ? "easy" : clues >= 33 ? "medium" : "hard";
                json = "{\"puzzle\":\"" + compact(puzzle) + "\",\"clues\":" + clues +
                        ",\"name\":\"" + name + " (" + clues + " clues)\"," +
                        "\"genMillis\":" + String.format(Locale.ROOT, "%.0f", ms) + "}";
            }
            sendJson(ex, 200, json);
        } finally {
            ex.close();
        }
    }

    private static void handleSolve(HttpExchange ex) throws IOException {
        try {
            String body;
            if ("POST".equals(ex.getRequestMethod())) {
                body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).trim();
            } else {
                body = query(ex).getOrDefault("puzzle", "");
            }
            if (!validPuzzle(body)) {
                sendJson(ex, 400, "{\"error\":\"puzzle must be 81 characters of digits 1-9, 0 or .\"}");
                return;
            }
            int[] puzzle = SudokuBoard.parse(body).cells();
            NcmStyleSolver.Result r = new NcmStyleSolver(new Random()).solve(puzzle);
            int[] ar = ArStyleSolver.solve(puzzle);
            boolean arSolved = ar != null && SudokuBoard.conflicts(ar) == 0;
            long t0 = System.nanoTime();
            int[] truth = DancingLinks.solve(puzzle);
            double micros = (System.nanoTime() - t0) / 1000.0;
            boolean verified = r.solved() && truth != null && Arrays.equals(truth, r.grid);

            StringBuilder sb = new StringBuilder();
            sb.append("{\"puzzle\":\"").append(compact(puzzle)).append("\",");
            sb.append("\"solved\":").append(r.solved()).append(",");
            sb.append("\"levels\":").append(r.levels).append(",");
            sb.append("\"restarts\":").append(r.restarts).append(",");
            sb.append("\"arSolved\":").append(arSolved).append(",");
            sb.append("\"dlxVerified\":").append(verified).append(",");
            sb.append("\"dlxMicros\":").append(String.format(Locale.ROOT, "%.1f", micros)).append(",");
            sb.append("\"trajectory\":[");
            for (int k = 0; k < r.trajectory.size(); k++) {
                int[] g = r.trajectory.get(k);
                if (k > 0) sb.append(',');
                sb.append("{\"grid\":\"").append(compact(g)).append("\",\"conflicts\":")
                        .append(SudokuBoard.conflicts(g)).append('}');
            }
            sb.append("]}");
            sendJson(ex, 200, sb.toString());
        } finally {
            ex.close();
        }
    }

    private static boolean validPuzzle(String s) {
        if (s == null || s.length() != 81) return false;
        for (char c : s.toCharArray()) {
            if ((c < '1' || c > '9') && c != '0' && c != '.') return false;
        }
        return true;
    }

    private static String compact(int[] cells) {
        StringBuilder sb = new StringBuilder(81);
        for (int d : cells) sb.append(d == 0 ? '.' : (char) ('0' + d));
        return sb.toString();
    }

    private static Map<String, String> query(HttpExchange ex) {
        Map<String, String> m = new HashMap<>();
        String q = ex.getRequestURI().getRawQuery();
        if (q == null) return m;
        for (String pair : q.split("&")) {
            int i = pair.indexOf('=');
            if (i > 0) {
                m.put(URLDecoder.decode(pair.substring(0, i), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8));
            }
        }
        return m;
    }

    private static byte[] indexPage() throws IOException {
        if (pageBytes == null) {
            pageBytes = ("<html><body style=\"font-family:sans-serif;background:#0b1020;color:#e8eefc\">" +
                    "<h2>web/index.html not found</h2><p>Start the server from the project root.</p></body></html>")
                    .getBytes(StandardCharsets.UTF_8);
            for (String candidate : new String[]{"web/index.html", "src/web/index.html", "index.html"}) {
                Path p = Path.of(candidate);
                if (Files.isRegularFile(p)) {
                    pageBytes = Files.readAllBytes(p);
                    break;
                }
            }
        }
        return pageBytes;
    }

    private static void sendJson(HttpExchange ex, int code, String json) throws IOException {
        send(ex, code, "application/json; charset=utf-8", json);
    }

    private static void send(HttpExchange ex, int code, String contentType, String text) throws IOException {
        byte[] b = text.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(b);
        }
    }
}
