package fr.chatcmds;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RNG meter: "!rng" answers the fill rate of your selected drop, "!rng <drop>" any other drop.
 *
 * Drops and required scores come from the NEU repo (resource chatcmds-rngscore.json): Catacombs floors
 * (F1-M7) and the six slayers. A drop is written "<category>_<drop>", for example m7_necron_handle.
 *
 * Your stored score and selected drop are taken, in this order, from: your SkyBlock profile (if the API
 * exposes them, searched automatically), the "RNG Meter - X Stored XP/Score" chat lines, and what you
 * saved with /cmd rng select and /cmd rng score.
 */
final class Rng {
    /** One drop of one meter. token example: "m7_necron_handle". */
    record Drop(String token, String category, String id, long required) {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern STORED = Pattern.compile("RNG Meter - ([\\d,]+) Stored (?:XP|Score)");
    private static final Map<String, String> SLAYERS = Map.of(
            "Revenant Horror", "revenant", "Tarantula Broodfather", "tarantula", "Sven Packmaster", "sven",
            "Voidgloom Seraph", "voidgloom", "Inferno Demonlord", "inferno", "Riftstalker Bloodfiend", "riftstalker");

    private static final Map<String, Drop> DROPS = new LinkedHashMap<>();
    private static final Map<String, Long> SCORES = new LinkedHashMap<>();
    private static String selected = "";
    private static Path file;

    private Rng() {}

    static void init(Path configDir) {
        file = configDir.resolve("chatcmds-rng.json");
        try (InputStream in = Rng.class.getResourceAsStream("/chatcmds-rngscore.json")) {
            if (in != null) {
                loadData(GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class));
            }
        } catch (Exception e) {
            System.err.println("[chatcmds] Could not read RNG data: " + e);
        }
        load();
    }

    /** Fills DROPS from the NEU format {"catacombs": {"F1": {ID: req}}, "slayer": {"Name": {ID: req}}}. */
    static synchronized void loadData(JsonObject root) {
        DROPS.clear();
        for (String group : new String[] {"catacombs", "slayer"}) {
            JsonObject g = Json.obj(root, group);
            if (g == null) continue;
            for (Map.Entry<String, JsonElement> cat : g.entrySet()) {
                String c = group.equals("slayer")
                        ? SLAYERS.getOrDefault(cat.getKey(), key(cat.getKey()))
                        : key(cat.getKey());
                if (!cat.getValue().isJsonObject()) continue;
                for (Map.Entry<String, JsonElement> d : cat.getValue().getAsJsonObject().entrySet()) {
                    String token = c + "_" + dropKey(d.getKey());
                    DROPS.put(token, new Drop(token, c, d.getKey(), d.getValue().getAsLong()));
                }
            }
        }
    }

    static String key(String s) {
        return s.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    /** "ULTIMATE_JERRY;1" -> "ultimate_jerry_1" */
    static String dropKey(String id) {
        return id.toLowerCase(Locale.ROOT).replace(';', '_');
    }

    /** "ULTIMATE_JERRY;1" -> "Ultimate Jerry 1" */
    static String pretty(String id) {
        StringBuilder sb = new StringBuilder();
        for (String w : id.replace(';', ' ').split("[_ ]+")) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }

    static synchronized void load() {
        SCORES.clear();
        selected = "";
        try {
            if (file == null || !Files.exists(file)) return;
            JsonObject root = GSON.fromJson(Files.readString(file), JsonObject.class);
            if (root == null) return;
            if (root.has("selected") && root.get("selected").isJsonPrimitive()) {
                selected = root.get("selected").getAsString();
            }
            JsonObject sc = Json.obj(root, "scores");
            if (sc != null) for (Map.Entry<String, JsonElement> e : sc.entrySet()) SCORES.put(e.getKey(), e.getValue().getAsLong());
        } catch (Exception e) {
            System.err.println("[chatcmds] Could not read RNG state: " + e);
        }
    }

    private static synchronized void save() {
        try {
            if (file == null) return;
            JsonObject root = new JsonObject();
            root.addProperty("selected", selected);
            JsonObject sc = new JsonObject();
            for (Map.Entry<String, Long> e : SCORES.entrySet()) sc.addProperty(e.getKey(), e.getValue());
            root.add("scores", sc);
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(root));
        } catch (Exception e) {
            System.err.println("[chatcmds] Could not save RNG state: " + e);
        }
    }

    // ---- tab completion ----

    static synchronized List<String> tokens() {
        return new ArrayList<>(DROPS.keySet());
    }

    static synchronized Drop get(String token) {
        return token == null ? null : DROPS.get(key(token));
    }

    static synchronized String selected() {
        return selected;
    }

    static synchronized boolean select(String token) {
        if (get(token) == null) return false;
        selected = key(token);
        save();
        return true;
    }

    static synchronized void setScore(String category, long score) {
        SCORES.put(category, Math.max(0, score));
        save();
    }

    static synchronized Long score(String category) {
        return SCORES.get(category);
    }

    // ---- chat ----

    /** Reads "RNG Meter - 12,345 Stored XP" and stores it for the category of the selected drop. */
    static void observe(String plainLine) {
        Matcher m = STORED.matcher(plainLine);
        if (!m.find()) return;
        Drop d = get(selected());
        if (d == null) return;
        try {
            setScore(d.category(), Long.parseLong(m.group(1).replace(",", "")));
        } catch (NumberFormatException ignored) {
            // ignore
        }
    }

    // ---- profile (API) ----

    /**
     * Tries to find the selected drop and the stored score in the profile. The API layout of the RNG
     * meter is not documented, so this looks for an object whose key contains "rng" and "meter" and reads
     * a string equal to a known drop and a number under the key of the category. Returns {drop|null, score|null}.
     */
    static Object[] fromApi(JsonObject member, String wantedCategory) {
        JsonObject meter = findMeter(member, 0);
        if (meter == null) return new Object[] {null, null};
        Drop dropFound = null;
        Long scoreFound = null;
        for (Map.Entry<String, JsonElement> e : meter.entrySet()) {
            JsonElement v = e.getValue();
            if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isString() && dropFound == null) {
                String s = v.getAsString();
                for (Drop d : tokens().stream().map(Rng::get).toList()) {
                    if (d.id().equalsIgnoreCase(s)) {
                        dropFound = d;
                        break;
                    }
                }
            } else if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber() && wantedCategory != null
                    && e.getKey().toLowerCase(Locale.ROOT).contains(wantedCategory)) {
                scoreFound = v.getAsLong();
            }
        }
        return new Object[] {dropFound, scoreFound};
    }

    private static JsonObject findMeter(JsonObject o, int depth) {
        if (o == null || depth > 6) return null;
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            String k = e.getKey().toLowerCase(Locale.ROOT);
            if (e.getValue().isJsonObject()) {
                if (k.contains("rng") && k.contains("meter")) return e.getValue().getAsJsonObject();
                JsonObject r = findMeter(e.getValue().getAsJsonObject(), depth + 1);
                if (r != null) return r;
            }
        }
        return null;
    }

    // ---- answer ----

    /** "Necron Handle (m7) : 12,500 rng / 100,000 req, 12% fill" */
    static String answer(Drop d, long score) {
        long s = Math.max(0, score);
        int pct = d.required() <= 0 ? 0 : (int) Math.min(100, Math.floor(s * 100.0 / d.required()));
        return pretty(d.id()) + " (" + d.category() + ") : "
                + String.format(Locale.US, "%,d rng / %,d req, %d%% fill", s, d.required(), pct);
    }

    /** token may be null/blank: then the selected drop is used. */
    static void run(String token, Out out) throws Exception {
        boolean explicit = token != null && !token.isBlank();
        Drop d = explicit ? get(token) : get(selected());
        if (explicit && d == null) {
            out.local("Unknown drop. Press Tab after /rng to list them (format <category>_<drop>, e.g. m7_necron_handle).");
            return;
        }
        Hypixel.Profile p = null;
        try {
            p = Hypixel.profile(out);
        } catch (Exception ignored) {
            // API unavailable: fall back on saved values
        }
        Long apiScore = null;
        if (p != null) {
            Object[] r = fromApi(p.member(), d != null ? d.category() : null);
            if (!explicit && r[0] != null) {
                d = (Drop) r[0];
                apiScore = (Long) r[1];
            } else if (r[1] != null) {
                apiScore = (Long) r[1];
            }
        }
        if (d == null) {
            out.local("No selected drop known. Choose it with /cmd rng select <drop> (Tab lists them), "
                    + "or use !rng <drop>.");
            return;
        }
        Long score = apiScore != null ? apiScore : score(d.category());
        if (score == null) {
            out.local("Score of " + d.category() + " unknown yet. It is read from the \"RNG Meter - X Stored\" chat line "
                    + "after a run, or set it with /cmd rng score <" + d.category() + "> <value>.");
            return;
        }
        out.say(answer(d, score));
    }
}
