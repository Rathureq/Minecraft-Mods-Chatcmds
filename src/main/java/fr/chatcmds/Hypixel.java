package fr.chatcmds;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Hypixel API calls. Always run off the main thread. Results are cached to limit requests. */
final class Hypixel {
    record Profile(JsonObject profile, JsonObject member) {}

    private static final long BAZAAR_TTL = 30_000;
    private static final long PROFILE_TTL = 60_000;
    private static final long AH_TTL = 60_000;

    private static JsonObject bazaarCache;
    private static long bazaarAt;
    /** uuid -> {Profile, time}, a few entries (yours and the players looked up recently). */
    private static final Map<String, Object[]> profileCache = new HashMap<>();
    /** player name (lower-case) -> uuid, from Mojang. */
    private static final Map<String, String> uuidCache = new HashMap<>();
    /** Times of the recent lookups of other players (rate limit). */
    private static final java.util.ArrayDeque<Long> lookups = new java.util.ArrayDeque<>();
    private static final int MAX_LOOKUPS_PER_MINUTE = 12;
    /** Player whose stats the current worker thread is reading, null = you. */
    private static final ThreadLocal<String> TARGET = new ThreadLocal<>();

    interface PlayerTask {
        void run(Out out) throws Exception;
    }

    /** Prefixes the public answers with the name of the player they are about. */
    private record NamedOut(Out delegate, String name) implements Out {
        @Override
        public void say(String message) {
            delegate.say(name + " - " + message);
        }

        @Override
        public void local(String message) {
            delegate.local(message);
        }
    }

    /**
     * Runs a stat command for another player (name != null) or for you (name == null).
     * Other players are looked up with your API key, so they are limited to 12 per minute.
     */
    static void asPlayer(String name, Out out, PlayerTask task) throws Exception {
        if (name == null) {
            task.run(out);
            return;
        }
        synchronized (Hypixel.class) {
            long now = System.currentTimeMillis();
            while (!lookups.isEmpty() && now - lookups.peekFirst() > 60_000) lookups.pollFirst();
            if (lookups.size() >= MAX_LOOKUPS_PER_MINUTE) {
                out.local("Too many player lookups, wait a few seconds.");
                return;
            }
            lookups.addLast(now);
        }
        TARGET.set(name);
        try {
            task.run(new NamedOut(out, name));
        } finally {
            TARGET.remove();
        }
    }

    /** UUID of a player name (Mojang), cached. Null after telling you when the player does not exist. */
    private static String uuidOf(String name, Out out) throws Exception {
        String key = name.toLowerCase(Locale.ROOT);
        synchronized (Hypixel.class) {
            String c = uuidCache.get(key);
            if (c != null) return c;
        }
        try {
            JsonObject o = Http.getJson("https://api.mojang.com/users/profiles/minecraft/" + name, null, null, "Mojang")
                    .getAsJsonObject();
            String id = o.has("id") ? o.get("id").getAsString() : null;
            if (id == null) throw new Http.ApiException("Mojang answered HTTP 404.");
            synchronized (Hypixel.class) {
                uuidCache.put(key, id);
            }
            return id;
        } catch (Http.ApiException e) {
            String m = e.getMessage() == null ? "" : e.getMessage();
            if (m.contains("HTTP 404") || m.contains("HTTP 204") || m.contains("HTTP 400")) {
                out.local("Player not found : " + name);
                return null;
            }
            throw e;
        }
    }
    private static final Map<String, String[]> binCache = new HashMap<>();

    private Hypixel() {}

    /** Forget cached data (called when the API key changes). */
    static synchronized void invalidate() {
        bazaarCache = null;
        profileCache.clear();
        binCache.clear();
    }

    // ---------- Bazaar ----------

    static synchronized JsonObject bazaarProducts() throws Exception {
        long now = System.currentTimeMillis();
        if (bazaarCache != null && now - bazaarAt < BAZAAR_TTL) return bazaarCache;
        JsonObject root = Http.getJson("https://api.hypixel.net/v2/skyblock/bazaar", null, null, "Hypixel")
                .getAsJsonObject();
        bazaarCache = root.getAsJsonObject("products");
        bazaarAt = now;
        return bazaarCache;
    }

    static void bazaarPrices(String productId, Out out) throws Exception {
        JsonObject product = bazaarProducts().getAsJsonObject(productId);
        if (product == null) {
            out.local("Product not found in the Bazaar: " + productId);
            return;
        }
        JsonObject q = product.getAsJsonObject("quick_status");
        out.say("insta buy : " + fmt(q.get("buyPrice").getAsDouble())
                + ", insta sell : " + fmt(q.get("sellPrice").getAsDouble()));
    }

    // ---------- Player profile ----------

    /** Selected SkyBlock profile of the player (cached 60 s). Null after telling the player why. */
    static Profile profile(Out out) throws Exception {
        if (Config.apiKey.isEmpty()) {
            out.local("Hypixel API key missing. Get one on developer.hypixel.net, then type /cmd key <your key>");
            return null;
        }
        String target = TARGET.get();
        String id = target != null ? uuidOf(target, out)
                : (Config.uuid.isEmpty() ? Config.uuidSupplier.get() : Config.uuid);
        if (id == null || id.isEmpty()) {
            if (target == null) out.local("Could not find your player UUID.");
            return null;
        }
        String plain = id.replace("-", "");
        long now = System.currentTimeMillis();
        synchronized (Hypixel.class) {
            Object[] c = profileCache.get(plain);
            if (c != null && now - (Long) c[1] < PROFILE_TTL) return (Profile) c[0];
        }

        JsonObject root = Http.getJson("https://api.hypixel.net/v2/skyblock/profiles?uuid=" + plain,
                "API-Key", Config.apiKey, "Hypixel").getAsJsonObject();
        JsonObject selected = null;
        JsonElement arr = root.get("profiles");
        if (arr != null && arr.isJsonArray()) {
            for (JsonElement e : arr.getAsJsonArray()) {
                JsonObject p = e.getAsJsonObject();
                if (p.has("selected") && p.get("selected").getAsBoolean()) {
                    selected = p;
                    break;
                }
            }
        }
        if (selected == null) {
            out.local(target != null ? "No selected SkyBlock profile found for " + target + "."
                    : "No selected SkyBlock profile found.");
            return null;
        }
        JsonObject member = Json.obj(Json.obj(selected, "members"), plain);
        if (member == null) {
            out.local(target != null ? target + " was not found in the selected profile."
                    : "You were not found in the selected profile.");
            return null;
        }
        Profile p = new Profile(selected, member);
        synchronized (Hypixel.class) {
            if (profileCache.size() >= 30) profileCache.clear();
            profileCache.put(plain, new Object[] {p, now});
        }
        return p;
    }

    // ---------- Dungeons ----------

    private static JsonObject dungeonType(JsonObject member, String type) {
        return Json.obj(Json.obj(Json.obj(member, "dungeons"), "dungeon_types"), type);
    }

    private static int completionsOf(JsonObject dungeon, int floor) {
        return (int) Math.round(Json.num(Json.obj(dungeon, "tier_completions"), String.valueOf(floor), 0));
    }

    /** !comp1 .. !comp7 : boss collection (a normal run counts 1, a master run counts 2). */
    static void completions(int floor, Out out) throws Exception {
        Profile p = profile(out);
        if (p == null) return;
        int normal = completionsOf(dungeonType(p.member(), "catacombs"), floor);
        int master = completionsOf(dungeonType(p.member(), "master_catacombs"), floor);
        int x = normal + 2 * master;
        int missingRuns = Math.max(0, (1000 - x + 1) / 2);
        int pct = Math.min(100, x * 100 / 1000);
        out.say("F" + floor + " collection : " + x + "/1000 (" + pct + "%), missing : "
                + missingRuns + " master runs (M" + floor + ")");
    }

    /** !pb1 .. !pb7 : master mode best time. */
    static void bestTime(int floor, Out out) throws Exception {
        Profile p = profile(out);
        if (p == null) return;
        JsonObject master = dungeonType(p.member(), "master_catacombs");
        String f = String.valueOf(floor);
        String label = "S+";
        double ms = Json.num(Json.obj(master, "fastest_time_s_plus"), f, 0);
        if (ms <= 0) {
            ms = Json.num(Json.obj(master, "fastest_time_s"), f, 0);
            label = "S";
        }
        if (ms <= 0) {
            ms = Json.num(Json.obj(master, "fastest_time"), f, 0);
            label = "";
        }
        if (ms <= 0) {
            out.say("PB M" + floor + " : no time");
            return;
        }
        long sec = Math.round(ms / 1000);
        out.say("PB M" + floor + (label.isEmpty() ? "" : " (" + label + ")") + " : "
                + (sec / 60) + ":" + String.format("%02d", sec % 60));
    }

    private static final double[] CATA_XP = {
            50, 125, 235, 395, 625, 955, 1425, 2095, 3045, 4385,
            6275, 8940, 12700, 17960, 25340, 35640, 50040, 70040, 97640, 135640,
            188140, 259640, 356640, 488640, 668640, 911640, 1239640, 1684640, 2284640, 3084640,
            4149640, 5559640, 7459640, 9959640, 13259640, 17559640, 23159640, 30359640, 39559640, 51559640,
            66559640, 85559640, 109559640, 139559640, 177559640, 225559640, 285559640, 360559640, 453559640, 569809640
    };

    static int cataLevel(double xp) {
        int lvl = 0;
        for (double t : CATA_XP) {
            if (xp >= t) lvl++;
            else break;
        }
        if (lvl >= 50) {
            lvl = Math.min(99, 50 + (int) Math.floor((xp - CATA_XP[49]) / 200_000_000d));
        }
        return lvl;
    }

    static void cataLevels(Out out) throws Exception {
        Profile p = profile(out);
        if (p == null) return;
        JsonObject cata = dungeonType(p.member(), "catacombs");
        JsonObject classes = Json.obj(Json.obj(p.member(), "dungeons"), "player_classes");
        out.say("cata level : " + cataLevel(Json.num(cata, "experience", 0))
                + ", archer : " + cataLevel(Json.num(Json.obj(classes, "archer"), "experience", 0))
                + ", mage : " + cataLevel(Json.num(Json.obj(classes, "mage"), "experience", 0))
                + ", tank : " + cataLevel(Json.num(Json.obj(classes, "tank"), "experience", 0))
                + ", bers : " + cataLevel(Json.num(Json.obj(classes, "berserk"), "experience", 0))
                + ", healer : " + cataLevel(Json.num(Json.obj(classes, "healer"), "experience", 0)));
    }

    // ---------- Other stats ----------

    static void bank(Out out) throws Exception {
        Profile p = profile(out);
        if (p == null) return;
        JsonObject banking = Json.obj(p.profile(), "banking");
        if (banking == null || !banking.has("balance")) {
            out.local("Bank API is disabled or empty on this profile.");
            return;
        }
        out.say("Bank : " + fmt(banking.get("balance").getAsDouble()) + " coins");
    }

    static void sbLevel(Out out) throws Exception {
        Profile p = profile(out);
        if (p == null) return;
        double xp = Json.num(Json.obj(p.member(), "leveling"), "experience", 0);
        out.say("SkyBlock level : " + (int) Math.floor(xp / 100));
    }

    static void magicalPower(Out out) throws Exception {
        Profile p = profile(out);
        if (p == null) return;
        JsonObject acc = Json.obj(p.member(), "accessory_bag_storage");
        if (acc == null || !acc.has("highest_magical_power")) {
            out.local("Magical Power not found in the API.");
            return;
        }
        out.say("Magical Power : " + acc.get("highest_magical_power").getAsInt());
    }

    // ---------- Auction House ----------

    static void necronHandle(Out out) throws Exception {
        lowestBin("Necron's Handle", out);
    }

    /** Lowest BIN of an item whose name contains the text (case-insensitive). Cached 60 s per query. */
    static void lowestBin(String query, Out out) throws Exception {
        String q = query.trim().toLowerCase(Locale.ROOT);
        synchronized (Hypixel.class) {
            String[] c = binCache.get(q);
            if (c != null && System.currentTimeMillis() - Long.parseLong(c[1]) < AH_TTL) {
                out.say(c[0]);
                return;
            }
        }
        JsonObject first = Http.getJson("https://api.hypixel.net/v2/skyblock/auctions?page=0", null, null, "Hypixel")
                .getAsJsonObject();
        int pages = first.get("totalPages").getAsInt();

        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<CompletableFuture<Object[]>> futures = new ArrayList<>();
            futures.add(CompletableFuture.completedFuture(lowestIn(first, q)));
            for (int i = 1; i < pages; i++) {
                final int page = i;
                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        return lowestIn(Http.getJson(
                                "https://api.hypixel.net/v2/skyblock/auctions?page=" + page,
                                null, null, "Hypixel").getAsJsonObject(), q);
                    } catch (Exception e) {
                        return new Object[] {Double.MAX_VALUE, ""};
                    }
                }, pool));
            }
            double min = Double.MAX_VALUE;
            String name = "";
            for (CompletableFuture<Object[]> f : futures) {
                Object[] r = f.get(25, TimeUnit.SECONDS);
                if ((Double) r[0] < min) {
                    min = (Double) r[0];
                    name = (String) r[1];
                }
            }
            String msg = (min == Double.MAX_VALUE)
                    ? "No \"" + query.trim() + "\" on sale (BIN) in the Auction House"
                    : cleanName(name) + " (BIN) : " + fmt(min);
            synchronized (Hypixel.class) {
                binCache.put(q, new String[] {msg, String.valueOf(System.currentTimeMillis())});
            }
            out.say(msg);
        } finally {
            pool.shutdownNow();
        }
    }

    /** Keeps letters, digits, space and a few signs (drops star glyphs and similar). */
    static String cleanName(String s) {
        return s.replaceAll("[^\\p{L}\\p{N} '+\\-\\[\\]]", "").replaceAll("\\s+", " ").trim();
    }

    /** {lowest price, item name} among the BIN auctions of this page whose name contains q (lower-case). */
    private static Object[] lowestIn(JsonObject page, String q) {
        double min = Double.MAX_VALUE;
        String name = "";
        JsonArray auctions = page.getAsJsonArray("auctions");
        if (auctions == null) return new Object[] {min, name};
        for (JsonElement e : auctions) {
            JsonObject a = e.getAsJsonObject();
            if (!a.has("bin") || !a.get("bin").getAsBoolean()) continue;
            if (a.has("claimed") && a.get("claimed").getAsBoolean()) continue;
            String n = a.get("item_name").getAsString();
            if (!n.toLowerCase(Locale.ROOT).contains(q)) continue;
            double price = a.get("starting_bid").getAsDouble();
            if (price < min) {
                min = price;
                name = n;
            }
        }
        return new Object[] {min, name};
    }

    // ---------- !pprice : any Bazaar item ----------

    private static final Map<String, String> BZ_ALIASES = Map.of(
            "COOKIE", "BOOSTER_COOKIE", "KISMET", "KISMET_FEATHER", "CHIM", "ENCHANTMENT_ULTIMATE_CHIMERA_1",
            "CHIMERA", "ENCHANTMENT_ULTIMATE_CHIMERA_1", "RECOMB", "RECOMBOBULATOR_3000",
            "RECOMBOBULATOR", "RECOMBOBULATOR_3000");

    /** Finds the Bazaar product id for what the player typed, or null. */
    static String resolveBazaar(Set<String> ids, String text) {
        String key = text.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_|_$", "");
        if (key.isEmpty()) return null;
        if (BZ_ALIASES.containsKey(key)) return BZ_ALIASES.get(key);
        if (ids.contains(key)) return key;
        if (ids.contains("ENCHANTMENT_" + key)) return "ENCHANTMENT_" + key;
        String[] words = key.split("_");
        String best = null;
        for (String id : ids) {
            boolean all = true;
            for (String w : words) {
                if (!id.contains(w)) {
                    all = false;
                    break;
                }
            }
            if (all && (best == null || id.length() < best.length())) best = id;
        }
        return best;
    }

    /** !pprice <item>: Bazaar price, or lowest BIN when the item is not in the Bazaar. */
    static void price(String text, Out out) throws Exception {
        if (text == null || text.isBlank()) {
            out.local("Usage : !pprice <item>   (example : !pprice booster cookie)");
            return;
        }
        if (text.trim().equalsIgnoreCase("handle")) {
            necronHandle(out);
            return;
        }
        JsonObject products = bazaarProducts();
        String id = resolveBazaar(products.keySet(), text);
        if (id == null) {
            lowestBin(text, out);
            return;
        }
        JsonObject q = products.getAsJsonObject(id).getAsJsonObject("quick_status");
        out.say(Rng.pretty(id) + " : insta buy : " + fmt(q.get("buyPrice").getAsDouble())
                + ", insta sell : " + fmt(q.get("sellPrice").getAsDouble()));
    }

    // ---------- !mayor ----------

    static void mayor(Out out) throws Exception {
        JsonObject root = Http.getJson("https://api.hypixel.net/v2/resources/skyblock/election", null, null, "Hypixel")
                .getAsJsonObject();
        JsonObject m = Json.obj(root, "mayor");
        if (m == null) {
            out.local("No mayor data.");
            return;
        }
        StringBuilder sb = new StringBuilder("Mayor : ").append(Text.plain(m.get("name").getAsString()));
        if (m.has("perks") && m.get("perks").isJsonArray()) {
            List<String> perks = new ArrayList<>();
            for (JsonElement e : m.getAsJsonArray("perks")) {
                perks.add(Text.plain(e.getAsJsonObject().get("name").getAsString()));
            }
            if (!perks.isEmpty()) sb.append(" (").append(String.join(", ", perks)).append(")");
        }
        JsonObject min = Json.obj(m, "minister");
        if (min != null && min.has("name")) {
            sb.append(" | Minister : ").append(Text.plain(min.get("name").getAsString()));
            JsonObject perk = Json.obj(min, "perk");
            if (perk != null && perk.has("name")) sb.append(" (").append(Text.plain(perk.get("name").getAsString())).append(")");
        }
        out.say(sb.toString());
    }

    // ---------- /cmd find : locate a field in your own profile ----------

    /** Lists the paths of JSON keys containing the text (case-insensitive). */
    static List<String> search(JsonElement root, String needle, int max) {
        List<String> found = new ArrayList<>();
        walk(root, "", needle.toLowerCase(Locale.ROOT), found, max, 0);
        return found;
    }

    private static void walk(JsonElement el, String path, String needle, List<String> found, int max, int depth) {
        if (el == null || found.size() >= max || depth > 10 || !el.isJsonObject()) return;
        for (var e : el.getAsJsonObject().entrySet()) {
            if (found.size() >= max) return;
            String p = path.isEmpty() ? e.getKey() : path + "." + e.getKey();
            JsonElement v = e.getValue();
            if (e.getKey().toLowerCase(Locale.ROOT).contains(needle)) {
                String shown = v.isJsonObject() ? "{" + v.getAsJsonObject().size() + " keys}"
                        : v.isJsonArray() ? "[" + v.getAsJsonArray().size() + " items]"
                        : v.toString();
                found.add(p + " = " + shown);
            }
            walk(v, p, needle, found, max, depth + 1);
        }
    }

    static void find(String needle, Out out) throws Exception {
        Profile p = profile(out);
        if (p == null) return;
        List<String> hits = search(p.member(), needle, 15);
        if (hits.isEmpty()) {
            out.local("No key containing '" + needle + "' in your profile.");
            return;
        }
        out.local("Keys containing '" + needle + "' (max 15):");
        for (String h : hits) out.local(h.length() > 200 ? h.substring(0, 200) + "..." : h);
    }

    static String fmt(double v) {
        if (v >= 1_000_000_000) return String.format(Locale.US, "%.2fB", v / 1_000_000_000);
        if (v >= 1_000_000) return String.format(Locale.US, "%.2fM", v / 1_000_000);
        if (v >= 1_000) return String.format(Locale.US, "%.1fk", v / 1_000);
        return String.format(Locale.US, "%.1f", v);
    }
}
