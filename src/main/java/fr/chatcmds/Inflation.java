package fr.chatcmds;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Price index of the "Consumer Inflation Index" basket (skyblock.finance), computed from the Bazaar.
 * 100 = the day the mod first measured it. Stored in .minecraft/config/chatcmds-data.json.
 */
final class Inflation {
    static final String[] IDS = {
            "BOOSTER_COOKIE", "RECOMBOBULATOR_3000", "ENCHANTMENT_ULTIMATE_CHIMERA_1",
            "PERFECT_JADE_GEM", "PERFECT_AMBER_GEM", "PERFECT_TOPAZ_GEM",
            "FIRST_MASTER_STAR", "SECOND_MASTER_STAR", "THIRD_MASTER_STAR",
            "FOURTH_MASTER_STAR", "FIFTH_MASTER_STAR",
            "IMPLOSION_SCROLL", "WITHER_SHIELD_SCROLL", "SHADOW_WARP_SCROLL"};
    static final double[] WEIGHTS = {100, 50, 16, 8, 8, 4, 5, 5, 5, 5, 5, 1, 1, 1};

    private static final long HOUR = 3_600_000L;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

    private static final List<double[]> HISTORY = new ArrayList<>(); // {timeMs, basketValue}
    private static double baseline;
    private static long baselineAt;
    private static Path file;

    private Inflation() {}

    static void init(Path configDir) {
        file = configDir.resolve("chatcmds-data.json");
        load();
    }

    // ---- persistence ----

    private static synchronized void load() {
        HISTORY.clear();
        baseline = 0;
        baselineAt = 0;
        try {
            if (file == null || !Files.exists(file)) return;
            JsonObject root = GSON.fromJson(Files.readString(file), JsonObject.class);
            if (root == null) return;
            baseline = Json.num(root, "baseline", 0);
            baselineAt = (long) Json.num(root, "baseline_at", 0);
            if (root.has("history") && root.get("history").isJsonArray()) {
                for (JsonElement e : root.getAsJsonArray("history")) {
                    JsonArray a = e.getAsJsonArray();
                    HISTORY.add(new double[]{a.get(0).getAsDouble(), a.get(1).getAsDouble()});
                }
            }
        } catch (Exception e) {
            System.err.println("[chatcmds] Could not read inflation data: " + e);
        }
    }

    private static synchronized void save() {
        try {
            if (file == null) return;
            JsonObject root = new JsonObject();
            root.addProperty("baseline", baseline);
            root.addProperty("baseline_at", baselineAt);
            JsonArray h = new JsonArray();
            for (double[] p : HISTORY) {
                JsonArray a = new JsonArray();
                a.add((long) p[0]);
                a.add(p[1]);
                h.add(a);
            }
            root.add("history", h);
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(root));
        } catch (Exception e) {
            System.err.println("[chatcmds] Could not save inflation data: " + e);
        }
    }

    /** Test helper: forget everything in memory. */
    static synchronized void resetMemory() {
        HISTORY.clear();
        baseline = 0;
        baselineAt = 0;
    }

    // ---- computation ----

    /** Weighted sum of the middle prices (between insta buy and insta sell) of the basket. */
    static double basket(JsonObject products) throws Http.ApiException {
        double sum = 0;
        for (int i = 0; i < IDS.length; i++) {
            JsonObject p = Json.obj(products, IDS[i]);
            JsonObject q = Json.obj(p, "quick_status");
            if (q == null) throw new Http.ApiException("Basket item missing from the Bazaar: " + IDS[i]);
            double mid = (q.get("buyPrice").getAsDouble() + q.get("sellPrice").getAsDouble()) / 2;
            sum += WEIGHTS[i] * mid;
        }
        return sum;
    }

    /** Records the basket value and returns the chat reply. */
    static synchronized String update(double basket, long now) {
        boolean fresh = baseline <= 0;
        if (fresh) {
            baseline = basket;
            baselineAt = now;
        }
        if (HISTORY.isEmpty() || now - (long) HISTORY.get(HISTORY.size() - 1)[0] >= 10 * 60_000L) {
            HISTORY.add(new double[]{now, basket});
        }
        HISTORY.removeIf(p -> now - (long) p[0] > 8 * 24 * HOUR);
        save();

        String date = DATE.format(Instant.ofEpochMilli(baselineAt));
        String head = "Inflation index : " + String.format(Locale.US, "%.1f", basket / baseline * 100);
        if (fresh) return head + " (first measure, base 100 = " + date + ", it moves from now on)";
        Double change = change24h(now, basket);
        String tail = change == null ? "24h : not enough history yet"
                : "24h : " + String.format(Locale.US, "%+.1f%%", change);
        return head + " (base 100 on " + date + "), " + tail;
    }

    /** Change since the stored point whose age is closest to 24 h (at least 20 h old), or null. */
    static synchronized Double change24h(long now, double basket) {
        double[] best = null;
        for (double[] p : HISTORY) {
            long age = now - (long) p[0];
            if (age < 20 * HOUR) continue;
            if (best == null || Math.abs(age - 24 * HOUR) < Math.abs(now - (long) best[0] - 24 * HOUR)) best = p;
        }
        return best == null ? null : (basket / best[1] - 1) * 100;
    }

    // ---- entry points ----

    static void run(Out out) throws Exception {
        out.say(update(basket(Hypixel.bazaarProducts()), System.currentTimeMillis()));
    }

    /** Silent measure, called every 30 minutes while you play. */
    static void snapshot() throws Exception {
        update(basket(Hypixel.bazaarProducts()), System.currentTimeMillis());
    }

    /** Makes today the new base 100. */
    static void reset(Out out) throws Exception {
        double b = basket(Hypixel.bazaarProducts());
        synchronized (Inflation.class) {
            HISTORY.clear();
            baseline = 0;
        }
        out.local(update(b, System.currentTimeMillis()));
    }
}
