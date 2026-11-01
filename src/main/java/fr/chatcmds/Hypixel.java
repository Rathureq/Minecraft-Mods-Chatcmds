package fr.chatcmds;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Appels à l'API Hypixel (toujours hors du thread principal). */
public class Hypixel {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    static void async(ThrowingRunnable r) {
        Thread t = new Thread(() -> {
            try {
                r.run();
            } catch (Exception e) {
                System.err.println("[chatcmds] Erreur : " + e);
                ChatCmdsClient.local("Erreur de requête : " + e);
            }
        }, "chatcmds-http");
        t.setDaemon(true);
        t.start();
    }

    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static JsonElement getJson(String url, String apiKey) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", "chatcmds-mod/1.1");
        if (apiKey != null && !apiKey.isEmpty()) b.header("API-Key", apiKey);
        HttpResponse<String> res = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            throw new IllegalStateException("HTTP " + res.statusCode());
        }
        return JsonParser.parseString(res.body());
    }

    private static JsonObject get(String url, String apiKey) throws Exception {
        return getJson(url, apiKey).getAsJsonObject();
    }

    // ---------- Bazaar : !pchim / !precomb ----------
    static void bazaarPrices(String productId) throws Exception {
        JsonObject root = get("https://api.hypixel.net/v2/skyblock/bazaar", null);
        JsonObject product = root.getAsJsonObject("products").getAsJsonObject(productId);
        if (product == null) {
            ChatCmdsClient.local("Produit introuvable au Bazaar");
            return;
        }
        JsonObject status = product.getAsJsonObject("quick_status");
        double buy = status.get("buyPrice").getAsDouble();   // insta buy
        double sell = status.get("sellPrice").getAsDouble(); // insta sell
        ChatCmdsClient.say("insta buy : " + fmt(buy) + ", insta sell : " + fmt(sell));
    }

    // ---------- Profil du joueur (config) ----------
    private record Profile(JsonObject profile, JsonObject member) {}

    private static Profile loadProfile() throws Exception {
        if (Config.apiKey.isEmpty()) {
            ChatCmdsClient.local("Clé API Hypixel manquante : remplis api_key dans .minecraft/config/chatcmds.json puis relance le jeu");
            return null;
        }
        String uuid = Config.uuid;
        if (uuid.isEmpty()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return null;
            uuid = mc.player.getUUID().toString();
        }
        String plain = uuid.replace("-", "");

        JsonObject root = get("https://api.hypixel.net/v2/skyblock/profiles?uuid=" + plain, Config.apiKey);
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
            ChatCmdsClient.local("Aucun profil SkyBlock sélectionné");
            return null;
        }
        JsonObject member = selected.getAsJsonObject("members").getAsJsonObject(plain);
        if (member == null) {
            ChatCmdsClient.local("Joueur introuvable dans le profil");
            return null;
        }
        return new Profile(selected, member);
    }

    // ---------- !comp1 .. !comp7 ----------
    static void completions(int floor) throws Exception {
        Profile p = loadProfile();
        if (p == null) return;
        int x = 0;
        JsonObject cata = cataObject(p.member());
        if (cata != null && cata.has("tier_completions")) {
            JsonObject tc = cata.getAsJsonObject("tier_completions");
            if (tc.has(String.valueOf(floor))) {
                x = (int) Math.round(tc.get(String.valueOf(floor)).getAsDouble());
            }
        }
        int missing = Math.max(0, Math.floorDiv(1000 - x, 2));
        ChatCmdsClient.say(x + "/1000, missing : " + missing + " m" + floor);
    }

    private static JsonObject cataObject(JsonObject member) {
        if (!member.has("dungeons")) return null;
        JsonObject types = member.getAsJsonObject("dungeons").getAsJsonObject("dungeon_types");
        return types == null ? null : types.getAsJsonObject("catacombs");
    }

    // ---------- !catalvl ----------
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
            lvl = 50 + (int) Math.floor((xp - CATA_XP[49]) / 200_000_000d);
            lvl = Math.min(lvl, 99);
        }
        return lvl;
    }

    private static double xpOf(JsonObject o) {
        return (o != null && o.has("experience")) ? o.get("experience").getAsDouble() : 0;
    }

    static void cataLevels() throws Exception {
        Profile p = loadProfile();
        if (p == null) return;
        JsonObject cata = cataObject(p.member());
        JsonObject classes = p.member().has("dungeons")
                ? p.member().getAsJsonObject("dungeons").getAsJsonObject("player_classes") : null;
        ChatCmdsClient.say("cata level : " + cataLevel(xpOf(cata))
                + ", archer : " + cataLevel(xpOf(sub(classes, "archer")))
                + ", mage : " + cataLevel(xpOf(sub(classes, "mage")))
                + ", tank : " + cataLevel(xpOf(sub(classes, "tank")))
                + ", bers : " + cataLevel(xpOf(sub(classes, "berserk")))
                + ", healer : " + cataLevel(xpOf(sub(classes, "healer"))));
    }

    private static JsonObject sub(JsonObject o, String k) {
        return (o != null && o.has(k) && o.get(k).isJsonObject()) ? o.getAsJsonObject(k) : null;
    }

    // ---------- !bank ----------
    static void bank() throws Exception {
        Profile p = loadProfile();
        if (p == null) return;
        JsonObject banking = sub(p.profile(), "banking");
        if (banking == null || !banking.has("balance")) {
            ChatCmdsClient.local("Bank : API banque désactivée ou vide");
            return;
        }
        ChatCmdsClient.say("Bank : " + fmt(banking.get("balance").getAsDouble()) + " coins");
    }

    // ---------- !pb1 .. !pb7 (meilleur temps S+) ----------
    static void bestTime(int floor) throws Exception {
        Profile p = loadProfile();
        if (p == null) return;
        JsonObject cata = cataObject(p.member());
        String f = String.valueOf(floor);
        String label = "S+";
        double ms = timeOf(cata, "fastest_time_s_plus", f);
        if (ms <= 0) {
            ms = timeOf(cata, "fastest_time_s", f);
            label = "S";
        }
        if (ms <= 0) {
            ms = timeOf(cata, "fastest_time", f);
            label = "";
        }
        if (ms <= 0) {
            ChatCmdsClient.say("PB F" + floor + " : aucun temps");
            return;
        }
        long sec = Math.round(ms / 1000);
        String t = (sec / 60) + ":" + String.format("%02d", sec % 60);
        ChatCmdsClient.say("PB F" + floor + (label.isEmpty() ? "" : " (" + label + ")") + " : " + t);
    }

    private static double timeOf(JsonObject cata, String field, String floor) {
        JsonObject o = sub(cata, field);
        return (o != null && o.has(floor)) ? o.get(floor).getAsDouble() : 0;
    }

    // ---------- !mp ----------
    static void magicalPower() throws Exception {
        Profile p = loadProfile();
        if (p == null) return;
        JsonObject acc = sub(p.member(), "accessory_bag_storage");
        if (acc == null || !acc.has("highest_magical_power")) {
            ChatCmdsClient.local("Magical Power introuvable dans l'API");
            return;
        }
        ChatCmdsClient.say("Magical Power : " + acc.get("highest_magical_power").getAsInt());
    }

    // ---------- !sblvl ----------
    static void sbLevel() throws Exception {
        Profile p = loadProfile();
        if (p == null) return;
        JsonObject leveling = sub(p.member(), "leveling");
        double xp = xpOf(leveling);
        ChatCmdsClient.say("Niveau SkyBlock : " + (int) Math.floor(xp / 100));
    }

    // ---------- !phandle (lowest BIN) ----------
    private static volatile long handleAt = 0;
    private static volatile String handleMsg = null;

    static void necronHandle() throws Exception {
        if (handleMsg != null && System.currentTimeMillis() - handleAt < 60_000) {
            ChatCmdsClient.say(handleMsg);
            return;
        }
        JsonObject first = get("https://api.hypixel.net/v2/skyblock/auctions?page=0", null);
        int pages = first.get("totalPages").getAsInt();

        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<CompletableFuture<Double>> futures = new ArrayList<>();
            futures.add(CompletableFuture.completedFuture(lowestHandle(first)));
            for (int i = 1; i < pages; i++) {
                final int page = i;
                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        return lowestHandle(get("https://api.hypixel.net/v2/skyblock/auctions?page=" + page, null));
                    } catch (Exception e) {
                        return Double.MAX_VALUE;
                    }
                }, pool));
            }
            double min = Double.MAX_VALUE;
            for (CompletableFuture<Double> f : futures) min = Math.min(min, f.get());
            handleMsg = (min == Double.MAX_VALUE)
                    ? "Aucun Necron's Handle en BIN à l'AH"
                    : "Necron's Handle (BIN) : " + fmt(min);
            handleAt = System.currentTimeMillis();
            ChatCmdsClient.say(handleMsg);
        } finally {
            pool.shutdown();
        }
    }

    private static double lowestHandle(JsonObject page) {
        double min = Double.MAX_VALUE;
        JsonArray auctions = page.getAsJsonArray("auctions");
        if (auctions == null) return min;
        for (JsonElement e : auctions) {
            JsonObject a = e.getAsJsonObject();
            if (!a.has("bin") || !a.get("bin").getAsBoolean()) continue;
            if (a.has("claimed") && a.get("claimed").getAsBoolean()) continue;
            if (!a.get("item_name").getAsString().contains("Necron's Handle")) continue;
            min = Math.min(min, a.get("starting_bid").getAsDouble());
        }
        return min;
    }

    // ---------- !inflation ----------
    // Panier du "Consumer Inflation Index" (skyblock.finance) : id Bazaar -> poids.
    private static final String[] INF_IDS = {
            "BOOSTER_COOKIE", "RECOMBOBULATOR_3000", "ENCHANTMENT_ULTIMATE_CHIMERA_1",
            "PERFECT_JADE_GEM", "PERFECT_AMBER_GEM", "PERFECT_TOPAZ_GEM",
            "FIRST_MASTER_STAR", "SECOND_MASTER_STAR", "THIRD_MASTER_STAR",
            "FOURTH_MASTER_STAR", "FIFTH_MASTER_STAR",
            "IMPLOSION_SCROLL", "WITHER_SHIELD_SCROLL", "SHADOW_WARP_SCROLL"};
    private static final double[] INF_W = {100, 50, 16, 8, 8, 4, 5, 5, 5, 5, 5, 1, 1, 1};

    static void inflation() throws Exception {
        JsonObject products = get("https://api.hypixel.net/v2/skyblock/bazaar", null).getAsJsonObject("products");
        double sum = 0, totalW = 0;
        for (int i = 0; i < INF_IDS.length; i++) {
            JsonObject p = products.getAsJsonObject(INF_IDS[i]);
            if (p == null) continue;
            JsonObject q = p.getAsJsonObject("quick_status");
            double mid = (q.get("buyPrice").getAsDouble() + q.get("sellPrice").getAsDouble()) / 2;
            sum += INF_W[i] * mid;
            totalW += INF_W[i];
        }
        if (totalW == 0) {
            ChatCmdsClient.local("Inflation : données Bazaar indisponibles");
            return;
        }
        double div = Config.inflationDivisor > 0 ? Config.inflationDivisor : totalW;
        ChatCmdsClient.say("Inflation actuelle : " + String.format(Locale.US, "%.2f", sum / div));
    }

    static String fmt(double v) {
        if (v >= 1_000_000_000) return String.format(Locale.US, "%.2fB", v / 1_000_000_000);
        if (v >= 1_000_000) return String.format(Locale.US, "%.2fM", v / 1_000_000);
        if (v >= 1_000) return String.format(Locale.US, "%.1fk", v / 1_000);
        return String.format(Locale.US, "%.1f", v);
    }
}
