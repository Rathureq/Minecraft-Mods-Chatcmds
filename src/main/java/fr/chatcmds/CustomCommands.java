package fr.chatcmds;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Commands created in game (/cmd add or the editor of /cmd gui).
 * Stored in .minecraft/config/chatcmds-commands.json (commands, use counters, saved variables).
 *
 * Placeholders usable in a response (they can be nested, the innermost is resolved first):
 *   {user} {me} {owner}       sender, you, "true" when the sender is you
 *   {arg} {arg2} {arg3} {args}  words typed after the command / everything typed
 *   {target}                  {arg}, or {user} when there is no argument
 *   {random:1-100} {percent} {pick:a|b|c}
 *   {time} {date}             "21:35" and "2026-11-16"
 *   {count} {mycount} {top}   uses of the command: total, by the sender, top 3 players
 *   {math:2*{arg}}            calculator (value only)
 *   {price:cookie} {sell:cookie}  Bazaar insta buy / insta sell price
 *   {replace:text|from|to}    replaces every "from" by "to"
 *   {set:x=5} {get:x}         variable for this answer only
 *   {store:x=5} {load:x}      variable kept between answers (shared by all commands)
 *   {if:A>B?then|elif:C==D?other|else}   condition (> < >= <= == !=), "else" is optional
 *   {get:x|default} {load:x|default}  value of a variable, or the default when it does not exist
 *   {inc:x} {inc:x=5} {sinc:x}        add 1 (or 5) to a variable (local / stored), the answer is empty
 *   {area}                    where you are ("Hub", "Dungeon"...), asked with /locraw
 *   {br}                      new line: the answer is sent as several chat messages (5 at most)
 *   {until:2026-12-25}        days left until that date (negative when past)
 */
final class CustomCommands {
    static final Pattern NAME = Pattern.compile("[a-z0-9_-]{1,20}");
    static final String HELP = "Placeholders: {user} {me} {owner} {arg} {arg2} {arg3} {args} {target} "
            + "{random:1-100} {percent} {pick:a|b|c} {time} {date} {count} {mycount} {top} {math:..} "
            + "{price:item} {sell:item} {replace:t|a|b} {set:x=1} {get:x} {store:x=1} {load:x} "
            + "{if:A>B?then|elif:C>D?other|else} {inc:x} {sinc:x} {area} {br} {until:2026-12-25}";

    /** Everything a response can depend on. */
    static final class Ctx {
        final String cmd;
        final String user;
        final String owner;
        final boolean isOwner;
        final String args;
        final Random rnd;
        /** Dry run (editor preview, validation): no network, no counters, sample values. */
        final boolean dry;
        /** False for a live test: stored variables are not written. */
        final boolean persist;
        final Map<String, String> vars = new HashMap<>();

        Ctx(String cmd, String user, String owner, boolean isOwner, String args, Random rnd, boolean dry) {
            this(cmd, user, owner, isOwner, args, rnd, dry, !dry);
        }

        Ctx(String cmd, String user, String owner, boolean isOwner, String args, Random rnd, boolean dry,
                boolean persist) {
            this.persist = persist;
            this.cmd = cmd;
            this.user = clean(user);
            this.owner = clean(owner);
            this.isOwner = isOwner;
            this.args = clean(args).trim();
            this.rnd = rnd;
            this.dry = dry;
        }

        /** Live test: real prices, counters and stored variables are read, nothing is written. */
        static Ctx live(String cmd, String user, String owner, String args) {
            return new Ctx(cmd, user, owner, false, args, new Random(), false, false);
        }

        /** Sample context used by the editor preview and the validation. */
        static Ctx sample(String owner) {
            return new Ctx("sample", "Steve", owner == null ? "You" : owner, false, "Alex Bob Carl", new Random(1), true);
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^{}]*)}");
    private static final Pattern RANDOM = Pattern.compile("random:(\\d{1,9})-(\\d{1,9})");
    private static final Pattern COND = Pattern.compile("^(.*?)(>=|<=|==|!=|>|<)(.*)$", Pattern.DOTALL);
    private static final Pattern VAR_NAME = Pattern.compile("[A-Za-z0-9_]{1,20}");
    private static final int MAX_VARS = 50;
    private static final int MAX_VALUE = 100;
    static final int MAX_LINES = 5;

    private static final Map<String, String> COMMANDS = new TreeMap<>();
    /** command -> player (lower-case) -> number of uses. */
    private static final Map<String, Map<String, Integer>> COUNTS = new HashMap<>();
    private static final Map<String, String> STORED = new TreeMap<>();
    private static Path file;

    private CustomCommands() {}

    static void init(Path configDir) {
        file = configDir.resolve("chatcmds-commands.json");
        load();
    }

    static synchronized void load() {
        COMMANDS.clear();
        COUNTS.clear();
        STORED.clear();
        try {
            if (file == null || !Files.exists(file)) return;
            JsonObject root = GSON.fromJson(Files.readString(file), JsonObject.class);
            if (root == null) return;
            JsonObject cmds = Json.obj(root, "commands");
            if (cmds != null) {
                for (var e : cmds.entrySet()) {
                    if (e.getValue().isJsonPrimitive()) COMMANDS.put(e.getKey(), e.getValue().getAsString());
                }
            }
            JsonObject counts = Json.obj(root, "counts");
            if (counts != null) {
                for (var c : counts.entrySet()) {
                    if (!c.getValue().isJsonObject()) continue;
                    Map<String, Integer> m = new HashMap<>();
                    for (var p : c.getValue().getAsJsonObject().entrySet()) m.put(p.getKey(), p.getValue().getAsInt());
                    COUNTS.put(c.getKey(), m);
                }
            }
            JsonObject vars = Json.obj(root, "vars");
            if (vars != null) {
                for (var v : vars.entrySet()) {
                    if (v.getValue().isJsonPrimitive()) STORED.put(v.getKey(), v.getValue().getAsString());
                }
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
            JsonObject counts = new JsonObject();
            for (var c : COUNTS.entrySet()) {
                JsonObject o = new JsonObject();
                c.getValue().forEach(o::addProperty);
                counts.add(c.getKey(), o);
            }
            JsonObject vars = new JsonObject();
            STORED.forEach(vars::addProperty);
            JsonObject root = new JsonObject();
            root.add("commands", cmds);
            root.add("counts", counts);
            root.add("vars", vars);
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
        if (Config.alias(name) != null) return "!" + name + " is already an alias.";
        String err = validate(response);
        if (err != null) return err;
        COMMANDS.put(name, response.trim());
        save();
        return null;
    }

    static synchronized boolean remove(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        boolean removed = COMMANDS.remove(n) != null;
        COUNTS.remove(n);
        if (removed) save();
        return removed;
    }

    static synchronized String get(String name) {
        return COMMANDS.get(name.toLowerCase(Locale.ROOT));
    }

    static synchronized Map<String, String> all() {
        return new TreeMap<>(COMMANDS);
    }

    /** Counts one use of the command by this player. */
    static synchronized void use(String cmd, String player) {
        COUNTS.computeIfAbsent(cmd, k -> new HashMap<>()).merge(player.toLowerCase(Locale.ROOT), 1, Integer::sum);
        save();
    }

    private static synchronized int total(String cmd) {
        int t = 0;
        for (int n : COUNTS.getOrDefault(cmd, Map.of()).values()) t += n;
        return t;
    }

    private static synchronized int countOf(String cmd, String player) {
        return COUNTS.getOrDefault(cmd, Map.of()).getOrDefault(player.toLowerCase(Locale.ROOT), 0);
    }

    private static synchronized String top(String cmd) {
        List<Map.Entry<String, Integer>> l = new ArrayList<>(COUNTS.getOrDefault(cmd, Map.of()).entrySet());
        l.sort((a, b) -> b.getValue() != a.getValue().intValue()
                ? Integer.compare(b.getValue(), a.getValue()) : a.getKey().compareTo(b.getKey()));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(3, l.size()); i++) {
            if (i > 0) sb.append(", ");
            sb.append(l.get(i).getKey()).append(" (").append(l.get(i).getValue()).append(')');
        }
        return sb.length() == 0 ? "nobody yet" : sb.toString();
    }

    private static synchronized String stored(String name) {
        return STORED.getOrDefault(name, "");
    }

    private static synchronized boolean hasStored(String name) {
        return STORED.containsKey(name);
    }

    private static synchronized void store(String name, String value) {
        if (!STORED.containsKey(name) && STORED.size() >= MAX_VARS) return;
        STORED.put(name, value);
        save();
    }

    /** True if the answer must be built off the game thread (network, /locraw) or sent as several messages. */
    static boolean needsAsync(String template) {
        return template.contains("price:") || template.contains("sell:") || template.contains("{area}")
                || template.contains("{br}");
    }

    /** Checks the response and its placeholders. Returns an error message, or null if valid. */
    static String validate(String response) {
        if (response == null || response.isBlank()) return "The response is empty.";
        String r = response.trim();
        if (r.length() > 200) return "The response is too long (200 characters max).";
        if (r.startsWith("/") || r.startsWith("!")) return "A response cannot start with / or !.";
        // dry run: anything still between braces afterwards is not a valid placeholder
        String dry = render(r, Ctx.sample("You"));
        Matcher left = PLACEHOLDER.matcher(dry);
        if (left.find()) return "Unknown or invalid placeholder " + left.group(0) + ". " + HELP;
        if (dry.split("\n", -1).length > MAX_LINES) return "Too many lines ({br}): " + MAX_LINES + " at most.";
        return null;
    }

    /** What the response would say (editor preview): never uses the network, counters or saved variables. */
    static String preview(String response, String owner) {
        if (response == null || response.isBlank()) return "";
        return render(response, Ctx.sample(owner)).replace("\n", " / ");
    }

    /** Removes the characters that would let a chat message inject placeholders or break a condition. */
    static String clean(String s) {
        return s == null ? "" : s.replaceAll("[{}|?]", "");
    }

    // ---- rendering ----

    /** Fills the placeholders of a response. Nested ones are resolved from the inside out. */
    static String render(String template, Ctx ctx) {
        String cur = template;
        for (int i = 0; i < 8; i++) {
            String next = pass(cur, ctx);
            if (next.equals(cur)) break;
            cur = next;
        }
        return cur;
    }

    /** True if "a op b" holds: numbers are compared as numbers, anything else as text (ignoring case). */
    static boolean compare(String a, String op, String b) {
        int c;
        try {
            c = Double.compare(Double.parseDouble(a), Double.parseDouble(b));
        } catch (NumberFormatException e) {
            c = a.compareToIgnoreCase(b);
        }
        return switch (op) {
            case ">" -> c > 0;
            case "<" -> c < 0;
            case ">=" -> c >= 0;
            case "<=" -> c <= 0;
            case "==" -> c == 0;
            default -> c != 0;
        };
    }

    /**
     * Result of an {if:...} body (the text after "if:"), or null when malformed.
     * Form: cond?then|else, where else can be "elif:cond?then|else" or "else:text" or just text.
     */
    static String evalIf(String body) {
        int q = body.indexOf('?');
        if (q < 0) return null;
        Matcher c = COND.matcher(body.substring(0, q));
        if (!c.matches()) return null;
        String rest = body.substring(q + 1);
        int bar = rest.indexOf('|');
        String yes = bar < 0 ? rest : rest.substring(0, bar);
        String no = bar < 0 ? "" : rest.substring(bar + 1);
        if (compare(c.group(1).trim(), c.group(2), c.group(3).trim())) return yes;
        if (no.startsWith("elif:")) return evalIf(no.substring(5));
        if (no.startsWith("else:")) return no.substring(5);
        return no;
    }

    private static String word(String args, int index) {
        if (args.isEmpty()) return "";
        String[] w = args.split("\\s+");
        return index < w.length ? w[index] : "";
    }

    private static String cut(String s) {
        return s.length() > MAX_VALUE ? s.substring(0, MAX_VALUE) : s;
    }

    private static String pass(String template, Ctx ctx) {
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String p = m.group(1);
            String rep = resolve(p, ctx);
            m.appendReplacement(sb, Matcher.quoteReplacement(rep == null ? m.group(0) : rep));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static Double parseNum(String s) {
        try {
            return Double.valueOf(s.trim());
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 3.0 becomes "3", 2.5 stays "2.5". */
    static String numText(double v) {
        return v == Math.rint(v) && Math.abs(v) < 1e15 ? String.valueOf((long) v) : String.valueOf(v);
    }

    private static void setVar(Ctx ctx, boolean persist, String name, String value) {
        if (persist) {
            if (ctx.persist) store(name, value);
            else ctx.vars.put("s:" + name, value);
        } else if (ctx.vars.size() < MAX_VARS) {
            ctx.vars.put(name, value);
        }
    }

    private static String getVar(Ctx ctx, boolean persist, String name, String def) {
        if (!persist) return ctx.vars.getOrDefault(name, def);
        if (ctx.vars.containsKey("s:" + name)) return ctx.vars.get("s:" + name);
        if (ctx.dry) return def;
        return hasStored(name) ? stored(name) : def;
    }

    /** Value of one placeholder (text between braces), or null when it is not valid. */
    private static String resolve(String p, Ctx ctx) {
        switch (p) {
            case "user": return ctx.user;
            case "me": return ctx.owner;
            case "owner": return String.valueOf(ctx.isOwner);
            case "arg": return word(ctx.args, 0);
            case "arg2": return word(ctx.args, 1);
            case "arg3": return word(ctx.args, 2);
            case "args": return ctx.args;
            case "target": return word(ctx.args, 0).isEmpty() ? ctx.user : word(ctx.args, 0);
            case "percent": return ctx.rnd.nextInt(101) + "%";
            case "time": return LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
            case "date": return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            case "count": return ctx.dry ? "1" : String.valueOf(total(ctx.cmd));
            case "mycount": return ctx.dry ? "1" : String.valueOf(countOf(ctx.cmd, ctx.user));
            case "br": return "\n";
            case "area": return ctx.dry ? "Hub" : Area.get();
            case "top": return ctx.dry ? "steve (3), alex (1)" : top(ctx.cmd);
            default: break;
        }
        Matcher r = RANDOM.matcher(p);
        if (r.matches()) {
            long lo = Long.parseLong(r.group(1));
            long hi = Long.parseLong(r.group(2));
            if (lo > hi) {
                long t = lo;
                lo = hi;
                hi = t;
            }
            return String.valueOf(lo + (long) (ctx.rnd.nextDouble() * (hi - lo + 1)));
        }
        if (p.startsWith("pick:")) {
            List<String> opts = new ArrayList<>();
            for (String s : p.substring(5).split("\\|")) if (!s.isBlank()) opts.add(s.trim());
            return opts.isEmpty() ? null : opts.get(ctx.rnd.nextInt(opts.size()));
        }
        if (p.startsWith("if:")) return evalIf(p.substring(3));
        if (p.startsWith("math:")) return MathEval.calc(p.substring(5));
        if (p.startsWith("replace:")) {
            String[] a = p.substring(8).split("\\|", -1);
            return a.length == 3 && !a[1].isEmpty() ? a[0].replace(a[1], a[2]) : null;
        }
        if (p.startsWith("set:") || p.startsWith("store:")) {
            boolean persist = p.startsWith("store:");
            String body = p.substring(persist ? 6 : 4);
            int eq = body.indexOf('=');
            if (eq < 1 || !VAR_NAME.matcher(body.substring(0, eq).trim()).matches()) return null;
            setVar(ctx, persist, body.substring(0, eq).trim(), cut(body.substring(eq + 1)));
            return "";
        }
        if (p.startsWith("inc:") || p.startsWith("sinc:")) {
            boolean persist = p.startsWith("sinc:");
            String body = p.substring(persist ? 5 : 4);
            int eq = body.indexOf('=');
            String name = (eq < 0 ? body : body.substring(0, eq)).trim();
            if (!VAR_NAME.matcher(name).matches()) return null;
            String step = eq < 0 ? "1" : body.substring(eq + 1).trim();
            if (parseNum(step) == null) return null;
            Double cur = parseNum(getVar(ctx, persist, name, "0"));
            setVar(ctx, persist, name, numText((cur == null ? 0 : cur) + parseNum(step)));
            return "";
        }
        if (p.startsWith("get:") || p.startsWith("load:")) {
            boolean persist = p.startsWith("load:");
            String body = p.substring(persist ? 5 : 4);
            int bar = body.indexOf('|');
            String name = (bar < 0 ? body : body.substring(0, bar)).trim();
            if (!VAR_NAME.matcher(name).matches()) return null;
            return getVar(ctx, persist, name, bar < 0 ? "" : body.substring(bar + 1));
        }
        if (p.startsWith("until:")) {
            try {
                LocalDate d = LocalDate.parse(p.substring(6).trim());
                return String.valueOf(ChronoUnit.DAYS.between(LocalDate.now(), d));
            } catch (RuntimeException e) {
                return null;
            }
        }
        if (p.startsWith("price:") || p.startsWith("sell:")) {
            String item = p.substring(p.indexOf(':') + 1).trim();
            if (item.isEmpty()) return null;
            if (ctx.dry) return "1.2k";
            try {
                var products = Hypixel.bazaarProducts();
                String id = Hypixel.resolveBazaar(products.keySet(), item);
                if (id == null) return "?";
                var q = products.getAsJsonObject(id).getAsJsonObject("quick_status");
                return Hypixel.fmt(q.get(p.startsWith("price:") ? "buyPrice" : "sellPrice").getAsDouble());
            } catch (Exception e) {
                return "?";
            }
        }
        return null;
    }
}
