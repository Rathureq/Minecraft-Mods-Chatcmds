package fr.chatcmds;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads every chat line, and answers "!command" typed by any player.
 * No Minecraft dependency: the output goes through {@link Out}.
 */
final class Dispatcher {
    /**
     * Recognized formats (Hypixel or vanilla):
     *   [MVP+] Name: !paff     Guild > [MVP+] Name [GM]: !pchim     Party > [MVP+] Name: !comp1
     */
    static final Pattern CHAT_LINE = Pattern.compile(
            "^(?:(?:Guild|Party|Co-op|From|To) > )?(?:\\[[^\\]]+\\] ?)*(\\w{1,16})(?: \\[[^\\]]+\\])?: (!\\S.*)$");
    private static final Pattern BLOCKED = Pattern.compile("^We blocked your comment \"(.*)\" because");
    private static final Pattern LASTFM_USER = Pattern.compile("[A-Za-z][A-Za-z0-9_-]{1,14}");

    /** Minimum delay between two answers to the same command. */
    static volatile long cooldownMs = 3000;

    private static final String[] ROASTS = {
            "have you seen your dungeon level? Even the tutorial thinks you're slow.",
            "your game strategy is watching others play and saying \"gg\".",
            "you take longer to pick your armor than a boss takes to kill you.",
            "your networth is so low that the Bazaar asks you to come back later.",
            "even a zombie dodges better than you.",
            "you flip on the Bazaar like I work out: in theory only.",
            "your lag is an excuse, and so is your skill.",
            "you're the type of player who dies while looking at the map.",
            "your Magical Power is so low that even ghosts ignore you.",
            "you have more deaths than coins in your bank.",
            "a pig with a sword would do better than you in the Catacombs.",
            "you're the reason the \"leave game\" button was invented.",
            "even your pet looks at you with pity.",
            "your aim is so bad that arrows turn around to flee from you.",
            "you're one click away from becoming a legend... but you miss the click.",
    };

    interface Task {
        void run() throws Exception;
    }

    private final Out out;
    private final Supplier<String> owner;
    private final Map<String, Long> lastUse = new ConcurrentHashMap<>();

    Dispatcher(Out out, Supplier<String> owner) {
        this.out = out;
        this.owner = owner;
    }

    /** Runs a task on a background thread and reports errors only to you. */
    static void async(Out out, Task task) {
        Thread t = new Thread(() -> {
            try {
                task.run();
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                out.local("Error : " + msg);
            }
        }, "chatcmds-worker");
        t.setDaemon(true);
        t.start();
    }

    /** Called for every incoming chat or system line. */
    void onMessage(String raw) {
        String text = Text.plain(raw);
        if (text.startsWith("We blocked your comment")) {
            Matcher b = BLOCKED.matcher(text);
            out.local("Hypixel blocked" + (b.find() ? " : \"" + b.group(1) + "\"" : " one of your messages")
                    + ". Find the word that triggered the filter and hide it with /cc censor add <word>.");
            return;
        }
        PartyRoles.observe(text);
        Rng.observe(text);
        Matcher m = CHAT_LINE.matcher(text);
        if (m.matches()) handle(m.group(1), m.group(2));
    }

    void handle(String sender, String line) {
        String[] parts = line.trim().split("\\s+", 2);
        String cmd = parts[0].substring(1).toLowerCase(Locale.ROOT);
        String args = parts.length > 1 ? parts[1].trim() : "";
        if (cmd.isEmpty()) return;

        String me = owner.get();
        boolean isOwner = me != null && sender.equalsIgnoreCase(me);
        Config.reloadIfChanged();
        if (!isOwner && Config.isMuted(sender)) return;

        BuiltIns.Cmd builtin = BuiltIns.find(cmd);
        String aliasTarget = builtin == null && CustomCommands.get(cmd) == null ? Config.alias(cmd) : null;
        if (aliasTarget != null) {
            String[] t = aliasTarget.split("\\s+", 2);
            cmd = t[0].toLowerCase(Locale.ROOT);
            args = t.length > 1 ? (args.isEmpty() ? t[1] : t[1] + " " + args) : args;
            builtin = BuiltIns.find(cmd);
        }
        String custom = builtin == null ? CustomCommands.get(cmd) : null;
        if (builtin == null && custom == null) return;
        if (builtin != null && builtin.ownerOnly() && !isOwner) return;
        if (!cooldownOk(cooldownKey(cmd))) return;

        if (custom != null) {
            out.say(CustomCommands.render(custom, sender, Text.firstWord(args), ThreadLocalRandom.current()));
            return;
        }
        run(cmd, args, sender, me);
    }

    private static String cooldownKey(String cmd) {
        if (cmd.matches("comp[1-7]")) return "comp";
        if (cmd.matches("pb[1-7]")) return "pb";
        return cmd;
    }

    private boolean cooldownOk(String key) {
        long now = System.currentTimeMillis();
        Long last = lastUse.get(key);
        if (last != null && now - last < cooldownMs) return false;
        lastUse.put(key, now);
        return true;
    }

    private void run(String cmd, String args, String sender, String me) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        switch (cmd) {
            case "paff" -> paff(args);
            case "math" -> out.say(MathEval.evaluateSafe(args));
            case "pchim" -> async(out, () -> Hypixel.bazaarPrices("ENCHANTMENT_ULTIMATE_CHIMERA_1", out));
            case "precomb" -> async(out, () -> Hypixel.bazaarPrices("RECOMBOBULATOR_3000", out));
            case "pprice" -> async(out, () -> Hypixel.price(args, out));
            case "ah" -> {
                String q = Text.itemQuery(args);
                if (q.isEmpty()) out.local("Usage : !ah <item>   (example : !ah necron's handle)");
                else async(out, () -> Hypixel.lowestBin(q, out));
            }
            case "mayor" -> async(out, () -> Hypixel.mayor(out));
            case "pets" -> async(out, () -> Stats.pets(out));
            case "skills" -> async(out, () -> Stats.skills(out));
            case "slayer" -> async(out, () -> Stats.slayer(out));
            case "purse" -> async(out, () -> Stats.purse(out));
            case "pcookie" -> async(out, () -> Hypixel.bazaarPrices("BOOSTER_COOKIE", out));
            case "pkismet" -> async(out, () -> Hypixel.bazaarPrices("KISMET_FEATHER", out));
            case "phandle" -> async(out, () -> Hypixel.necronHandle(out));
            case "inflation" -> async(out, () -> Inflation.run(out));
            case "catalvl" -> async(out, () -> Hypixel.cataLevels(out));
            case "bank" -> async(out, () -> Hypixel.bank(out));
            case "sblvl" -> async(out, () -> Hypixel.sbLevel(out));
            case "mp" -> async(out, () -> Hypixel.magicalPower(out));
            case "role" -> out.say(PartyRoles.summary());
            case "rng" -> rng(args);
            case "playing_music" -> async(out, () -> LastFm.nowPlaying(out));
            case "music" -> out.say("Best music ever : Bad Omen");
            case "kayoux" -> out.say("KAYOUX");
            case "credit" -> out.say("Mod made by Rathureq in 26.2, Github : Rathureq");
            case "angrelin" -> out.say("Angrelin will come to your house in " + rnd.nextInt(1, 365)
                    + " days (maybe, nobody really knows)");
            case "roast" -> {
                String who = Text.firstWord(args);
                out.say((who == null ? sender : who) + ", " + ROASTS[rnd.nextInt(ROASTS.length)]);
            }
            case "metal" -> {
                int p = rnd.nextInt(1, 101);
                String who = Text.firstWord(args);
                out.say(who == null ? "I love metal at " + p + "%" : who + " loves metal at " + p + "%");
            }
            case "link" -> link(args);
            case "cm-inch" -> unit(args);
            case "mute" -> mute(args, me);
            case "unmute" -> unmute(args);
            default -> {
                if (cmd.matches("comp[1-7]")) {
                    int floor = cmd.charAt(4) - '0';
                    async(out, () -> Hypixel.completions(floor, out));
                } else if (cmd.matches("pb[1-7]")) {
                    int floor = cmd.charAt(2) - '0';
                    async(out, () -> Hypixel.bestTime(floor, out));
                }
            }
        }
    }

    // ---- !paff (the only command that answers in French) ----

    private void paff(String args) {
        int cm = ThreadLocalRandom.current().nextInt(6, 23); // 6..22
        String size = "inch".equals(Config.unit)
                ? String.format(Locale.US, "%.1f inch", cm / 2.54)
                : cm + " cm";
        String who = Text.firstWord(args);
        out.say(who == null ? "Mon paff fait " + size : "Le paff de " + who + " fait " + size);
    }

    // ---- !rng ----

    private void rng(String args) {
        String token = args.isBlank() ? null : args.trim().split("\\s+")[0];
        async(out, () -> Rng.run(token, out));
    }

    // ---- owner-only commands (the answer is shown only to you) ----

    private void link(String args) {
        String name = args.split("\\s+")[0];
        if (!LASTFM_USER.matcher(name).matches()) {
            out.local("Usage : !link <your Last.fm username>");
            return;
        }
        Config.lastfmUser = name;
        Config.save();
        out.local("Last.fm account linked : " + name);
    }

    private void unit(String args) {
        String a = args.trim().toLowerCase(Locale.ROOT);
        if (a.equals("cm")) {
            Config.unit = "cm";
        } else if (a.equals("inch") || a.equals("in")) {
            Config.unit = "inch";
        } else {
            out.local("Current unit : " + Config.unit + ". Usage : !cm-inch cm|inch");
            return;
        }
        Config.save();
        out.local("!paff now uses " + Config.unit + ".");
    }

    private void mute(String args, String me) {
        String name = args.split("\\s+")[0];
        if (!Text.isPlayerName(name)) {
            out.local("Usage : !mute <player>");
            return;
        }
        if (me != null && name.equalsIgnoreCase(me)) {
            out.local("You cannot mute yourself.");
            return;
        }
        Config.mute(name);
        out.local(name + " is muted : their commands are ignored.");
    }

    private void unmute(String args) {
        String name = args.split("\\s+")[0];
        if (!Text.isPlayerName(name)) {
            out.local("Usage : !unmute <player>");
            return;
        }
        out.local(Config.unmute(name) ? name + " is no longer muted." : name + " was not muted.");
    }
}
