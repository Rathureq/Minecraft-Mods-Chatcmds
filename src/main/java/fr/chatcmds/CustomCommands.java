package fr.chatcmds;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Commands created in game with /cc add. Stored in .minecraft/config/chatcmds-commands.json.
 *
 * Placeholders usable in the response:
 *   {random:1-100}   random integer (inclusive)
 *   {percent}        random percentage, e.g. 57%
 *   {pick:a|b|c}     random item of a list
 *   {user}           name of the player who typed the command
 *   {arg}            first word typed after the command (empty if none)
 *   {target}         {arg}, or {user} when there is no argument
 */
final class CustomCommands {
    static final Pattern NAME = Pattern.compile("[a-z0-9_-]{1,20}");
    static final String HELP =
            "Placeholders: {random:1-100} {percent} {pick:a|b|c} {user} {arg} {target}";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^{}]*)}");
    private static final Pattern RANDOM = Pattern.compile("random:(\\d{1,9})-(\\d{1,9})");
    private static final Map<String, String> COMMANDS = new TreeMap<>();
    private static Path file;

    private CustomCommands() {}

    static void init(Path configDir) {
        file = configDir.resolve("chatcmds-commands.json");
        load();
    }

    static synchronized void load() {
        COMMANDS.clear();
        try {
            if (file == null || !Files.exists(file)) return;
            JsonObject root = GSON.fromJson(Files.readString(file), JsonObject.class);
            if (root == null) return;
            JsonObject cmds = Json.obj(root, "commands");
            if (cmds == null) return;
            for (var e : cmds.entrySet()) {
                if (e.getValue().isJsonPrimitive()) COMMANDS.put(e.getKey(), e.getValue().getAsString());
            }
        } catch (Exception e) {
            System.err.println("[chatcmds] Could not read custom commands: " + e);
        }
    }

    private static synchronized void save() {
        try {
            if (file == null) return;
            JsonObject cmds = new JsonObject();
            COMMANDS.forEach(cmds::addProperty);
            JsonObject root = new JsonObject();
            root.add("commands", cmds);
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(root));
        } catch (Exception e) {
            System.err.println("[chatcmds] Could not save custom commands: " + e);
        }
    }

    /** Adds or replaces a command. Returns an error message, or null on success. */
    static synchronized String add(String name, String response) {
        name = name.toLowerCase(Locale.ROOT);
        if (name.startsWith("!")) name = name.substring(1);
        if (!NAME.matcher(name).matches()) return "Invalid name. Use 1-20 letters, digits, _ or -.";
        if (BuiltIns.find(name) != null) return "!" + name + " is a built-in command.";
        String err = validate(response);
        if (err != null) return err;
        COMMANDS.put(name, response.trim());
        save();
        return null;
    }

    static synchronized boolean remove(String name) {
        boolean removed = COMMANDS.remove(name.toLowerCase(Locale.ROOT)) != null;
        if (removed) save();
        return removed;
    }

    static synchronized String get(String name) {
        return COMMANDS.get(name.toLowerCase(Locale.ROOT));
    }

    static synchronized Map<String, String> all() {
        return new TreeMap<>(COMMANDS);
    }

    /** Checks the response and its placeholders. Returns an error message, or null if valid. */
    static String validate(String response) {
        if (response == null || response.isBlank()) return "The response is empty.";
        String r = response.trim();
        if (r.length() > 200) return "The response is too long (200 characters max).";
        if (r.startsWith("/") || r.startsWith("!")) return "A response cannot start with / or !.";
        Matcher m = PLACEHOLDER.matcher(r);
        while (m.find()) {
            String p = m.group(1);
            if (!isKnown(p)) return "Unknown placeholder {" + p + "}. " + HELP;
        }
        return null;
    }

    private static boolean isKnown(String p) {
        if (p.equals("user") || p.equals("arg") || p.equals("target") || p.equals("percent")) return true;
        if (RANDOM.matcher(p).matches()) return true;
        if (p.startsWith("pick:")) return !pickOptions(p).isEmpty();
        return false;
    }

    private static List<String> pickOptions(String p) {
        List<String> out = new ArrayList<>();
        for (String s : p.substring("pick:".length()).split("\\|")) {
            if (!s.isBlank()) out.add(s.trim());
        }
        return out;
    }

    /** Fills the placeholders of a response. */
    static String render(String template, String user, String arg, Random rnd) {
        String a = (arg == null) ? "" : arg;
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String p = m.group(1);
            String rep;
            Matcher r = RANDOM.matcher(p);
            if (p.equals("user")) rep = user;
            else if (p.equals("arg")) rep = a;
            else if (p.equals("target")) rep = a.isEmpty() ? user : a;
            else if (p.equals("percent")) rep = rnd.nextInt(101) + "%";
            else if (r.matches()) {
                long lo = Long.parseLong(r.group(1));
                long hi = Long.parseLong(r.group(2));
                if (lo > hi) { long t = lo; lo = hi; hi = t; }
                rep = String.valueOf(lo + (long) (rnd.nextDouble() * (hi - lo + 1)));
            } else if (p.startsWith("pick:") && !pickOptions(p).isEmpty()) {
                List<String> opts = pickOptions(p);
                rep = opts.get(rnd.nextInt(opts.size()));
            } else {
                rep = m.group(0); // unknown placeholder: leave as typed
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(rep));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
