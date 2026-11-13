package fr.chatcmds;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Settings stored in .minecraft/config/chatcmds.json. Edited in game with /cc ... */
final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static volatile String apiKey = "";
    static volatile String uuid = "";
    static volatile String lastfmKey = "";
    static volatile String lastfmUser = "";
    /** "cm" or "inch" (used by !paff). */
    static volatile String unit = "cm";
    private static final Set<String> MUTED = ConcurrentHashMap.newKeySet();

    /** Returns the UUID of the logged-in player (set by the mod entrypoint). */
    static volatile Supplier<String> uuidSupplier = () -> null;

    private static Path file;
    private static long loadedMtime = -1;

    private Config() {}

    static void init(Path configDir) {
        file = configDir.resolve("chatcmds.json");
    }

    static synchronized void load() {
        try {
            if (file == null) return;
            if (!Files.exists(file)) {
                save();
                return;
            }
            JsonObject o = GSON.fromJson(Files.readString(file), JsonObject.class);
            if (o != null) {
                apiKey = str(o, "api_key");
                uuid = str(o, "uuid");
                lastfmKey = str(o, "lastfm_key");
                lastfmUser = str(o, "lastfm_user");
                String u = str(o, "unit").toLowerCase(Locale.ROOT);
                unit = u.equals("inch") ? "inch" : "cm";
                MUTED.clear();
                if (o.has("muted") && o.get("muted").isJsonArray()) {
                    for (JsonElement e : o.getAsJsonArray("muted")) {
                        MUTED.add(e.getAsString().toLowerCase(Locale.ROOT));
                    }
                }
            }
            loadedMtime = Files.getLastModifiedTime(file).toMillis();
        } catch (Exception e) {
            System.err.println("[chatcmds] Could not read config: " + e);
        }
    }

    static synchronized void save() {
        try {
            if (file == null) return;
            JsonObject o = new JsonObject();
            o.addProperty("api_key", apiKey);
            o.addProperty("uuid", uuid);
            o.addProperty("lastfm_key", lastfmKey);
            o.addProperty("lastfm_user", lastfmUser);
            o.addProperty("unit", unit);
            JsonArray muted = new JsonArray();
            for (String m : new TreeSet<>(MUTED)) muted.add(m);
            o.add("muted", muted);
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(o));
            loadedMtime = Files.getLastModifiedTime(file).toMillis();
        } catch (Exception e) {
            System.err.println("[chatcmds] Could not save config: " + e);
        }
    }

    /** Picks up manual edits of the file without restarting the game. */
    static synchronized void reloadIfChanged() {
        try {
            if (file != null && Files.exists(file)
                    && Files.getLastModifiedTime(file).toMillis() != loadedMtime) {
                load();
            }
        } catch (Exception ignored) {
        }
    }

    // ---- helpers ----

    static boolean isMuted(String name) {
        return name != null && MUTED.contains(name.toLowerCase(Locale.ROOT));
    }

    static void mute(String name) {
        MUTED.add(name.toLowerCase(Locale.ROOT));
        save();
    }

    static boolean unmute(String name) {
        boolean removed = MUTED.remove(name.toLowerCase(Locale.ROOT));
        save();
        return removed;
    }

    static Set<String> muted() {
        return new TreeSet<>(MUTED);
    }

    /** "not set" or "set (...abcd)" without exposing the key. */
    static String mask(String key) {
        if (key == null || key.isEmpty()) return "not set";
        return "set (..." + key.substring(Math.max(0, key.length() - 4)) + ")";
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString().trim() : "";
    }
}
