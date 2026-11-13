package fr.chatcmds;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** JSON helpers (no Minecraft dependency). */
final class Json {
    private Json() {}

    static JsonObject obj(JsonObject o, String key) {
        if (o == null || !o.has(key)) return null;
        JsonElement e = o.get(key);
        return e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    static double num(JsonObject o, String key, double def) {
        if (o == null || !o.has(key)) return def;
        JsonElement e = o.get(key);
        return (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) ? e.getAsDouble() : def;
    }

    /** Resolves a dot-separated path such as "slayer.slayer_bosses.zombie.xp". Returns null if absent. */
    static JsonElement path(JsonObject root, String dotPath) {
        JsonElement cur = root;
        for (String part : dotPath.split("\\.")) {
            if (cur == null || !cur.isJsonObject()) return null;
            cur = cur.getAsJsonObject().get(part);
        }
        return cur;
    }
}
