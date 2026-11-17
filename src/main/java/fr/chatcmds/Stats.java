package fr.chatcmds;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Player stats read from the SkyBlock profile: !pets !skills !slayer !purse. */
final class Stats {
    private static final Gson GSON = new Gson();
    private static JsonObject levels;

    /** Skills shown by !skills and used for the average. */
    private static final String[] MAIN = {"farming", "mining", "combat", "foraging", "fishing",
            "enchanting", "alchemy", "taming"};
    private static final String[] SLAYERS = {"zombie", "spider", "wolf", "enderman", "blaze", "vampire"};
    private static final String[] SLAYER_NAMES = {"Zombie", "Spider", "Wolf", "Enderman", "Blaze", "Vampire"};

    private Stats() {}

    private static synchronized JsonObject levels() {
        if (levels == null) {
            try (InputStream in = Stats.class.getResourceAsStream("/chatcmds-levels.json")) {
                levels = GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
            } catch (Exception e) {
                throw new IllegalStateException("level data missing");
            }
        }
        return levels;
    }

    private static long[] longs(JsonArray a) {
        long[] r = new long[a.size()];
        for (int i = 0; i < r.length; i++) r[i] = a.get(i).getAsLong();
        return r;
    }

    // ---------- pure logic (tested without Minecraft) ----------

    /** Level reached with this xp: xpPerLevel[i] is the xp needed to go from level i+1 to i+2. Starts at 1. */
    static int levelFromCosts(double xp, long[] costs) {
        int level = 1;
        double left = xp;
        for (long c : costs) {
            if (left < c) break;
            left -= c;
            level++;
        }
        return level;
    }

    /** Skill level: xpTable[i] = xp for level i+1 (not cumulative). Starts at 0. */
    static int skillLevel(double xp, long[] xpTable) {
        int level = 0;
        double left = xp;
        for (long c : xpTable) {
            if (left < c) break;
            left -= c;
            level++;
        }
        return level;
    }

    /** Skill level with the fraction towards the next one, e.g. 49.37. */
    static double skillLevelExact(double xp, long[] xpTable) {
        int lvl = skillLevel(xp, xpTable);
        if (lvl >= xpTable.length) return lvl;
        double used = 0;
        for (int i = 0; i < lvl; i++) used += xpTable[i];
        return lvl + (xp - used) / xpTable[lvl];
    }

    static int slayerLevel(double xp, long[] thresholds) {
        int level = 0;
        for (long t : thresholds) if (xp >= t) level++;
        return level;
    }

    /** Rarity order used for the Tier Boost item. */
    private static final List<String> RARITIES = List.of("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC");

    static int petLevel(String type, String rarity, double xp) {
        JsonObject lv = levels();
        int offset = Json.obj(lv, "pet_rarity_offset") != null
                && lv.getAsJsonObject("pet_rarity_offset").has(rarity)
                ? lv.getAsJsonObject("pet_rarity_offset").get(rarity).getAsInt() : 0;
        long[] base = longs(lv.getAsJsonArray("pet_levels"));
        int to = Math.min(base.length, offset + 99);
        List<Long> costs = new ArrayList<>();
        for (int i = offset; i < to; i++) costs.add(base[i]);
        JsonObject custom = Json.obj(lv, "custom_pet_leveling");
        if (custom != null && custom.has(type)) {
            for (long c : longs(custom.getAsJsonObject(type).getAsJsonArray("pet_levels"))) costs.add(c);
        }
        long[] arr = new long[costs.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = costs.get(i);
        return levelFromCosts(xp, arr);
    }

    static String commas(double v) {
        return String.format(Locale.US, "%,d", Math.round(v));
    }

    // ---------- commands ----------

    static void purse(Out out) throws Exception {
        Hypixel.Profile p = Hypixel.profile(out);
        if (p == null) return;
        JsonObject cur = Json.obj(p.member(), "currencies");
        double v = Json.num(cur, "coin_purse", Json.num(p.member(), "coin_purse", -1));
        if (v < 0) {
            out.local("Purse not found in this profile.");
            return;
        }
        out.say("Purse : " + commas(v) + " coins (" + Hypixel.fmt(v) + ")");
    }

    static void pets(Out out) throws Exception {
        Hypixel.Profile p = Hypixel.profile(out);
        if (p == null) return;
        JsonObject pd = Json.obj(p.member(), "pets_data");
        JsonArray pets = pd != null && pd.has("pets") && pd.get("pets").isJsonArray() ? pd.getAsJsonArray("pets") : null;
        if (pets == null) {
            out.local("No pet data in this profile.");
            return;
        }
        for (JsonElement e : pets) {
            JsonObject pet = e.getAsJsonObject();
            if (!pet.has("active") || !pet.get("active").getAsBoolean()) continue;
            String type = pet.get("type").getAsString();
            String tier = pet.has("tier") ? pet.get("tier").getAsString() : "COMMON";
            if (pet.has("heldItem") && pet.get("heldItem").isJsonPrimitive()
                    && "PET_ITEM_TIER_BOOST".equals(pet.get("heldItem").getAsString())) {
                int i = RARITIES.indexOf(tier);
                if (i >= 0 && i + 1 < RARITIES.size()) tier = RARITIES.get(i + 1);
            }
            int lvl = petLevel(type, tier, Json.num(pet, "exp", 0));
            out.say("Active pet : " + Rng.pretty(type) + " (" + Rng.pretty(tier) + "), level " + lvl);
            return;
        }
        out.say("No active pet");
    }

    static void skills(Out out) throws Exception {
        Hypixel.Profile p = Hypixel.profile(out);
        if (p == null) return;
        JsonObject pdata = Json.obj(p.member(), "player_data");
        JsonObject exp = Json.obj(pdata, "experience");
        long[] table = longs(levels().getAsJsonArray("leveling_xp"));
        StringBuilder sb = new StringBuilder();
        double sum = 0;
        int n = 0;
        for (String s : MAIN) {
            double xp = skillXp(p.member(), exp, s);
            if (xp < 0) continue;
            sum += skillLevelExact(xp, table);
            n++;
            if (sb.length() > 0) sb.append(", ");
            sb.append(Rng.pretty(s)).append(' ').append(skillLevel(xp, table));
        }
        if (n == 0) {
            out.local("Skill data not found in this profile (the Skills API may be disabled).");
            return;
        }
        out.say("Skills : " + sb + " | average " + String.format(Locale.US, "%.2f", sum / n));
    }

    /** New layout (player_data.experience.SKILL_X) or old one (experience_skill_x). -1 if absent. */
    private static double skillXp(JsonObject member, JsonObject exp, String skill) {
        double v = Json.num(exp, "SKILL_" + skill.toUpperCase(Locale.ROOT), -1);
        if (v < 0) v = Json.num(member, "experience_skill_" + skill, -1);
        return v;
    }

    static void slayer(Out out) throws Exception {
        Hypixel.Profile p = Hypixel.profile(out);
        if (p == null) return;
        JsonObject bosses = Json.obj(Json.obj(p.member(), "slayer"), "slayer_bosses");
        if (bosses == null) {
            out.local("No slayer data in this profile.");
            return;
        }
        JsonObject xpTables = levels().getAsJsonObject("slayer_xp");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < SLAYERS.length; i++) {
            double xp = Json.num(Json.obj(bosses, SLAYERS[i]), "xp", 0);
            int lvl = slayerLevel(xp, longs(xpTables.getAsJsonArray(SLAYERS[i])));
            if (sb.length() > 0) sb.append(", ");
            sb.append(SLAYER_NAMES[i]).append(' ').append(lvl).append(" (").append(commas(xp)).append(" xp)");
        }
        out.say("Slayers : " + sb);
    }

}
