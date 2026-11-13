package fr.chatcmds;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * RNG meter progress: !rng <category> <option> <drop>  ->  "x rng / x req, x% fill".
 *
 * The entries live in .minecraft/config/chatcmds-rng.json and are added in game with
 * /cc rng add <category> <option> <drop> <required> <path>
 * where <path> is the dot-separated place of your score in your SkyBlock profile
 * (use /cc find rng to locate it).
 */
final class Rng {
    record Entry(String category, String option, String drop, long required, String path) {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static Path file;

    private Rng() {}

    static void init(Path configDir) {
        file = configDir.resolve("chatcmds-rng.json");
        load();
    }

    /** Lower-case, spaces become underscores. */
    static String key(String s) {
        return s.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    static synchronized void load() {
        ENTRIES.clear();
        try {
            if (file == null || !Files.exists(file)) return;
            JsonObject root = GSON.fromJson(Files.readString(file), JsonObject.class);
            if (root == null || !root.has("entries")) return;
            for (JsonElement e : root.getAsJsonArray("entries")) {
                JsonObject o = e.getAsJsonObject();
                ENTRIES.add(new Entry(key(o.get("category").getAsString()), key(o.get("option").getAsString()),
                        key(o.get("drop").getAsString()), o.get("required").getAsLong(), o.get("path").getAsString()));
            }
        } catch (Exception e) {
            System.err.println("[chatcmds] Could not read RNG entries: " + e);
        }
    }

    private static synchronized void save() {
        try {
            if (file == null) return;
            JsonArray arr = new JsonArray();
            for (Entry e : ENTRIES) {
                JsonObject o = new JsonObject();
                o.addProperty("category", e.category());
                o.addProperty("option", e.option());
                o.addProperty("drop", e.drop());
                o.addProperty("required", e.required());
                o.addProperty("path", e.path());
                arr.add(o);
            }
            JsonObject root = new JsonObject();
            root.add("entries", arr);
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(root));
        } catch (Exception e) {
            System.err.println("[chatcmds] Could not save RNG entries: " + e);
        }
    }

    static synchronized void add(String category, String option, String drop, long required, String path) {
        remove(category, option, drop);
        ENTRIES.add(new Entry(key(category), key(option), key(drop), required, path));
        save();
    }

    static synchronized boolean remove(String category, String option, String drop) {
        boolean removed = ENTRIES.removeIf(e -> e.category().equals(key(category))
                && e.option().equals(key(option)) && e.drop().equals(key(drop)));
        if (removed) save();
        return removed;
    }

    static synchronized List<Entry> all() {
        return new ArrayList<>(ENTRIES);
    }

    static synchronized Entry find(String category, String option, String drop) {
        for (Entry e : ENTRIES) {
            if (e.category().equals(key(category)) && e.option().equals(key(option)) && e.drop().equals(key(drop))) {
                return e;
            }
        }
        return null;
    }

    // ---- lists used for tab completion ----

    static synchronized List<String> categories() {
        Set<String> s = new LinkedHashSet<>();
        for (Entry e : ENTRIES) s.add(e.category());
        return new ArrayList<>(s);
    }

    static synchronized List<String> options(String category) {
        Set<String> s = new LinkedHashSet<>();
        for (Entry e : ENTRIES) if (e.category().equals(key(category))) s.add(e.option());
        return new ArrayList<>(s);
    }

    static synchronized List<String> drops(String category, String option) {
        Set<String> s = new LinkedHashSet<>();
        for (Entry e : ENTRIES) {
            if (e.category().equals(key(category)) && e.option().equals(key(option))) s.add(e.drop());
        }
        return new ArrayList<>(s);
    }

    // ---- answer ----

    /** "12,500 rng / 100,000 req, 12% fill" */
    static String answer(Entry e, double score) {
        long s = Math.max(0, Math.round(score));
        int pct = e.required() <= 0 ? 0 : (int) Math.min(100, Math.floor(s * 100.0 / e.required()));
        return e.drop() + " : " + String.format(Locale.US, "%,d rng / %,d req, %d%% fill", s, e.required(), pct);
    }

    static void run(String category, String option, String drop, Out out) throws Exception {
        Entry e = find(category, option, drop);
        if (e == null) {
            out.local("Unknown RNG entry. List them with /cc rng list, add one with /cc rng add.");
            return;
        }
        Hypixel.Profile p = Hypixel.profile(out);
        if (p == null) return;
        JsonElement v = Json.path(p.member(), e.path());
        double score = 0;
        if (v != null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber()) {
            score = v.getAsDouble();
        } else {
            out.local("Path '" + e.path() + "' not found in your profile, score counted as 0. Check it with /cc find.");
        }
        out.say(answer(e, score));
    }
}
