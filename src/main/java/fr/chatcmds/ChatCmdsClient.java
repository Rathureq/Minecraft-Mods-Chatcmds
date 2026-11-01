package fr.chatcmds;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Écoute le chat : dès qu'un joueur (toi ou un autre) écrit "!commande ...",
 * le mod envoie la réponse dans le chat public.
 */
public class ChatCmdsClient implements ClientModInitializer {

    /**
     * Formats reconnus (Hypixel ou vanilla) :
     *   [MVP+] Pseudo: !paff
     *   [123] [VIP] Pseudo: !math 2+2
     *   Guild > [MVP+] Pseudo [GM]: !pchim
     *   Party > [MVP+] Pseudo: !comp1
     */
    private static final Pattern CHAT_LINE = Pattern.compile(
            "^(?:(?:Guild|Party|Co-op|From|To) > )?(?:\\[[^\\]]+\\] ?)*(\\w{1,16})(?: \\[[^\\]]+\\])?: (!\\S.*)$");

    private static final Pattern COLOR_CODES = Pattern.compile("§.");
    private static final Pattern COMP = Pattern.compile("^!comp([1-7])$");
    private static final Pattern PB = Pattern.compile("^!pb([1-7])$");

    /** Anti-spam : délai minimum entre deux réponses à une même commande. */
    private static final long COOLDOWN_MS = 3000;
    private final Map<String, Long> lastUse = new ConcurrentHashMap<>();

    @Override
    public void onInitializeClient() {
        Config.load();

        // Hypixel envoie les messages des joueurs comme messages "système" (GAME).
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) handle(message);
        });
        // Serveurs vanilla : vrais messages de joueurs signés.
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, timestamp) -> handle(message));
    }

    private void handle(Component message) {
        String text = COLOR_CODES.matcher(message.getString()).replaceAll("").trim();
        Matcher m = CHAT_LINE.matcher(text);
        if (!m.matches()) return;

        String[] parts = m.group(2).trim().split("\\s+", 2);
        String cmd = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1] : "";

        String key = cmd.startsWith("!comp") ? "!comp" : cmd.matches("^!pb[1-7]$") ? "!pb" : cmd;
        long now = System.currentTimeMillis();
        Long last = lastUse.get(key);
        if (last != null && now - last < COOLDOWN_MS) return;

        Matcher comp = COMP.matcher(cmd);
        switch (cmd) {
            case "!paff" -> {
                touch(key, now);
                int cm = ThreadLocalRandom.current().nextInt(6, 23); // 6..22 inclus
                String who = firstWord(args);
                say(who == null ? "Mon paff fait " + cm + " cm"
                                : "Le paff de " + who + " fait " + cm + " cm");
            }
            case "!roast" -> {
                touch(key, now);
                String who = firstWord(args);
                if (who == null) who = m.group(1);
                String jab = ROASTS[ThreadLocalRandom.current().nextInt(ROASTS.length)];
                say(who + ", " + jab);
            }
            case "!math" -> {
                touch(key, now);
                say(MathEval.evaluateSafe(args));
            }
            case "!pchim" -> {
                touch(key, now);
                Hypixel.async(() -> Hypixel.bazaarPrices("ENCHANTMENT_ULTIMATE_CHIMERA_1"));
            }
            case "!precomb" -> {
                touch(key, now);
                Hypixel.async(() -> Hypixel.bazaarPrices("RECOMBOBULATOR_3000"));
            }
            case "!phandle" -> {
                touch(key, now);
                Hypixel.async(Hypixel::necronHandle);
            }
            case "!inflation" -> {
                touch(key, now);
                Hypixel.async(Hypixel::inflation);
            }
            case "!catalvl" -> {
                touch(key, now);
                Hypixel.async(Hypixel::cataLevels);
            }
            case "!bank" -> {
                touch(key, now);
                Hypixel.async(Hypixel::bank);
            }
            case "!sblvl" -> {
                touch(key, now);
                Hypixel.async(Hypixel::sbLevel);
            }
            case "!music" -> {
                touch(key, now);
                say("Best music ever : Bad Omen");
            }
            case "!angrelin" -> {
                touch(key, now);
                int days = ThreadLocalRandom.current().nextInt(1, 365);
                say("Angrelin will come to your house in " + days + " days (maybe, nobody really knows)");
            }
            case "!kayoux" -> {
                touch(key, now);
                say("KAYOUX");
            }
            case "!metal" -> {
                touch(key, now);
                int p = ThreadLocalRandom.current().nextInt(1, 101)
                String who = firstWord(args);
                say(who == null ? "I love metal at " + p + "%"
                                : who + " loves metal at " + p + "%");
            case "!credit" -> {
                touch(key, now);
                say("Mod made by Rathureq in 26.2, Github : Rathureq");
            }
            case "!mp" -> {
                touch(key, now);
                Hypixel.async(Hypixel::magicalPower);
            }
            default -> {
                Matcher pb = PB.matcher(cmd);
                if (pb.matches()) {
                    touch(key, now);
                    int floor = Integer.parseInt(pb.group(1));
                    Hypixel.async(() -> Hypixel.bestTime(floor));
                } else if (comp.matches()) {
                    touch(key, now);
                    int floor = Integer.parseInt(comp.group(1));
                    Hypixel.async(() -> Hypixel.completions(floor));
                }
            }
        }
    }

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

    /** Premier mot d'un argument s'il ressemble à un pseudo Minecraft, sinon null. */
    private static String firstWord(String args) {
        if (args == null || args.isBlank()) return null;
        String w = args.trim().split("\\s+")[0];
        return w.matches("\\w{1,16}") ? w : null;
    }

    private void touch(String key, long now) {
        lastUse.put(key, now);
    }

    /** Message visible uniquement par toi (diagnostic / erreurs). */
    static void local(String msg) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) {
                mc.player.sendSystemMessage(Component.literal("[chatcmds] " + msg));
            }
        });
    }

    /** Envoie un message dans le chat public (toujours sur le thread principal). */
    static void say(String msg) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.getConnection() != null) {
                mc.getConnection().sendChat(msg);
            }
        });
    }
}
