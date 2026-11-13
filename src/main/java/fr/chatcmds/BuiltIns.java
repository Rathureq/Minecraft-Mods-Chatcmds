package fr.chatcmds;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Registry of the built-in chat commands (used for dispatch, the GUI and name clashes). */
final class BuiltIns {
    record Cmd(String label, String description, boolean ownerOnly, List<String> names) {}

    static final List<Cmd> ALL = new ArrayList<>();

    static {
        add("paff [name]", "Random size (6-22 cm, French reply)", false, "paff");
        add("math <expr>", "Calculator: + - * / % ^ and parentheses", false, "math");
        add("pchim", "Chimera I Bazaar prices", false, "pchim");
        add("precomb", "Recombobulator 3000 Bazaar prices", false, "precomb");
        add("pcookie", "Booster Cookie Bazaar prices", false, "pcookie");
        add("phandle", "Lowest BIN price of Necron's Handle", false, "phandle");
        add("inflation", "Price index of the old basket (base 100)", false, "inflation");
        add("comp1-7", "Boss collection progress and master runs missing", false,
                "comp1", "comp2", "comp3", "comp4", "comp5", "comp6", "comp7");
        add("pb1-7", "Master mode best time of the floor", false,
                "pb1", "pb2", "pb3", "pb4", "pb5", "pb6", "pb7");
        add("catalvl", "Catacombs level and class levels", false, "catalvl");
        add("bank", "Bank balance", false, "bank");
        add("sblvl", "SkyBlock level", false, "sblvl");
        add("mp", "Magical Power", false, "mp");
        add("role", "Classes of the Party Finder members", false, "role");
        add("rng <cat> <opt> <drop>", "RNG meter progress (set up with /cc rng)", false, "rng");
        add("playing_music", "Song playing right now (Last.fm)", false, "playing_music");
        add("music", "Best music ever", false, "music");
        add("roast [name]", "Random roast", false, "roast");
        add("metal [name]", "Metal love percentage", false, "metal");
        add("angrelin", "Angrelin ETA", false, "angrelin");
        add("kayoux", "KAYOUX", false, "kayoux");
        add("credit", "Mod credits", false, "credit");
        add("link <lastfm_user>", "Link your Last.fm account", true, "link");
        add("cm-inch <cm|inch>", "Unit used by !paff", true, "cm-inch");
        add("mute <name>", "Ignore the commands of a player", true, "mute");
        add("unmute <name>", "Stop ignoring a player", true, "unmute");
    }

    private BuiltIns() {}

    private static void add(String label, String desc, boolean ownerOnly, String... names) {
        ALL.add(new Cmd(label, desc, ownerOnly, List.of(names)));
    }

    /** Finds the built-in command that answers to this name (lower-case, no "!"), or null. */
    static Cmd find(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        for (Cmd c : ALL) if (c.names().contains(n)) return c;
        return null;
    }
}
