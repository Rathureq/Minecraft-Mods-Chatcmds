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

        String key = cmd.startsWith("!comp") ? "!comp" : cmd;
        long now = System.currentTimeMillis();
        Long last = lastUse.get(key);
        if (last != null && now - last < COOLDOWN_MS) return;

        Matcher comp = COMP.matcher(cmd);
        switch (cmd) {
            case "!paff" -> {
                touch(key, now);
                int cm = ThreadLocalRandom.current().nextInt(6, 23); // 6..22 inclus
                say("Mon paff fait " + cm + " cm");
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
                say("Best music ever : https://open.spotify.com/intl-fr/artist/3Ri4H12KFyu98LMjSoij5V");
            }
            case "!kayoux" -> {
                touch(key, now);
                say("KAYOUX");
            }
            default -> {
                if (comp.matches()) {
                    touch(key, now);
                    int floor = Integer.parseInt(comp.group(1));
                    Hypixel.async(() -> Hypixel.completions(floor));
                }
            }
        }
    }

    private void touch(String key, long now) {
        lastUse.put(key, now);
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
