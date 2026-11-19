package fr.chatcmds;

import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Counts how many times each command was used, and by whom (config/chatcmds-usage.json). */
final class Usage {
    private Usage() {}

    private static Path file;
    /** command -> (player lower-case -> count) */
    private static final Map<String, Map<String, Integer>> DATA = new TreeMap<>();
    private static final Map<String, String> DISPLAY = new TreeMap<>();

    static void init(Path dir) {
        file = dir.resolve("chatcmds-usage.json");
        load();
    }

    static synchronized void record(String cmd, String player) {
        if (cmd == null || player == null || player.isBlank()) return;
        cmd = cmd.toLowerCase(Locale.ROOT);
        DISPLAY.put(player.toLowerCase(Locale.ROOT), player);
        DATA.computeIfAbsent(cmd, k -> new TreeMap<>()).merge(player.toLowerCase(Locale.ROOT), 1, Integer::sum);
        save();
    }

    /** Answer of !count: with no argument the top 5 commands, with a command the top 5 users of it. */
    static synchronized String top(String arg) {
        String q = arg == null ? "" : arg.trim().toLowerCase(Locale.ROOT);
        if (q.startsWith("!")) q = q.substring(1);
        int sp = q.indexOf(' ');
        if (sp > 0) q = q.substring(0, sp);
        if (DATA.isEmpty()) return "No command used yet.";
        if (q.isEmpty()) {
            List<String[]> rows = new ArrayList<>();
            for (var e : DATA.entrySet()) rows.add(new String[]{"!" + e.getKey(), String.valueOf(sum(e.getValue()))});
            return "Top commands : " + join(rows);
        }
        Map<String, Integer> m = DATA.get(q);
        if (m == null) return "!" + q + " has never been used.";
        List<String[]> rows = new ArrayList<>();
        for (var e : m.entrySet()) rows.add(new String[]{DISPLAY.getOrDefault(e.getKey(), e.getKey()), String.valueOf(e.getValue())});
        return "Top !" + q + " (" + sum(m) + " uses) : " + join(rows);
    }

    private static int sum(Map<String, Integer> m) {
        int s = 0;
        for (int v : m.values()) s += v;
        return s;
    }

    private static String join(List<String[]> rows) {
        rows.sort((a, b) -> Integer.parseInt(b[1]) - Integer.parseInt(a[1]));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(5, rows.size()); i++) {
            if (i > 0) sb.append(", ");
            sb.append(i + 1).append(". ").append(rows.get(i)[0]).append(" (").append(rows.get(i)[1]).append(")");
        }
        return sb.toString();
    }

    private static void load() {
        DATA.clear();
        try {
            if (file == null || !Files.exists(file)) return;
            JsonObject root = com.google.gson.JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var e : root.entrySet()) {
                Map<String, Integer> m = new TreeMap<>();
                for (var p : e.getValue().getAsJsonObject().entrySet()) m.put(p.getKey(), p.getValue().getAsInt());
                DATA.put(e.getKey(), m);
            }
        } catch (Exception ignored) {
        }
    }

    private static void save() {
        try {
            if (file == null) return;
            JsonObject root = new JsonObject();
            for (var e : DATA.entrySet()) {
                JsonObject o = new JsonObject();
                for (var p : e.getValue().entrySet()) o.addProperty(p.getKey(), p.getValue());
                root.add(e.getKey(), o);
            }
            Files.writeString(file, root.toString(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }
}
