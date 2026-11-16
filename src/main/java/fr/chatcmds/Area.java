package fr.chatcmds;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Where you are, for the {area} placeholder. Asks the server with /locraw (Hypixel answers with a JSON line),
 * hides that answer from your chat, and caches it for 15 seconds.
 */
final class Area {
    private static final long TTL = 15_000;
    private static CompletableFuture<String> pending;
    private static String cached = "";
    private static long cachedAt;

    private Area() {}

    /** Called for every incoming game message. Returns true when it was our /locraw answer (to hide it). */
    static synchronized boolean consume(String plainMessage) {
        CompletableFuture<String> p = pending;
        if (p == null || p.isDone()) return false;
        String s = plainMessage.trim();
        if (!s.startsWith("{") || !s.contains("\"server\"")) return false;
        String area = parse(s);
        cached = area;
        cachedAt = System.currentTimeMillis();
        p.complete(area);
        return true;
    }

    /** Area name from a /locraw answer, e.g. "Hub", "Dungeon", "Private Island", "Limbo". Pure function. */
    static String parse(String json) {
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            if (o.has("map") && !o.get("map").getAsString().isBlank()) return o.get("map").getAsString();
            if (o.has("mode") && !o.get("mode").getAsString().isBlank()) return Rng.pretty(o.get("mode").getAsString());
            if (o.has("gametype")) return Rng.pretty(o.get("gametype").getAsString().toLowerCase(Locale.ROOT));
            if (o.has("server")) return Rng.pretty(o.get("server").getAsString());
        } catch (RuntimeException e) {
            // not JSON
        }
        return "unknown";
    }

    /** Blocks up to 2.5 seconds: call it from a worker thread, never from the game thread. */
    static String get() {
        CompletableFuture<String> wait;
        synchronized (Area.class) {
            if (System.currentTimeMillis() - cachedAt < TTL && !cached.isEmpty()) return cached;
            if (pending == null || pending.isDone()) pending = new CompletableFuture<>();
            wait = pending;
        }
        Chat.command("locraw");
        try {
            return wait.get(2500, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            return "unknown";
        }
    }
}
