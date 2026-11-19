package fr.chatcmds;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Client-side slash commands. They never leave your computer (they are not sent to the server).
 *
 *   /dh                                  -> /warp dh
 *   /cmd ...                             -> settings, custom commands, GUI
 */
final class CmdCommands {
    private CmdCommands() {}

    static void register(CommandDispatcher<FabricClientCommandSource> d, Chat chat) {
        d.register(ClientCommands.literal("dh").executes(c -> {
            Chat.command("warp dh");
            return 1;
        }));
        d.register(cmdCommand(chat));
    }

    // ---------- /cmd ----------

    private static LiteralArgumentBuilder<FabricClientCommandSource> cmdCommand(Chat chat) {
        LiteralArgumentBuilder<FabricClientCommandSource> cmd = ClientCommands.literal("cmd");

        cmd.executes(c -> help(chat));
        cmd.then(ClientCommands.literal("help").executes(c -> help(chat)));
        cmd.then(ClientCommands.literal("gui").executes(c -> {
            ChatCmdsClient.openGui = true;
            return 1;
        }));
        cmd.then(ClientCommands.literal("status").executes(c -> status(chat)));
        cmd.then(ClientCommands.literal("reload").executes(c -> {
            Config.load();
            CustomCommands.load();
            Rng.load();
            Hypixel.invalidate();
            chat.local("Config reloaded.");
            return 1;
        }));

        // keys and accounts
        cmd.then(ClientCommands.literal("key").then(ClientCommands.argument("key", StringArgumentType.word())
                .executes(c -> {
                    Config.apiKey = StringArgumentType.getString(c, "key");
                    Config.save();
                    Hypixel.invalidate();
                    chat.local("Hypixel API key saved " + Config.mask(Config.apiKey) + ". Try !sblvl.");
                    return 1;
                })));
        cmd.then(ClientCommands.literal("lastfmkey").then(ClientCommands.argument("key", StringArgumentType.word())
                .executes(c -> {
                    Config.lastfmKey = StringArgumentType.getString(c, "key");
                    Config.save();
                    chat.local("Last.fm API key saved " + Config.mask(Config.lastfmKey) + ".");
                    return 1;
                })));
        cmd.then(ClientCommands.literal("link").then(ClientCommands.argument("user", StringArgumentType.word())
                .executes(c -> {
                    Config.lastfmUser = StringArgumentType.getString(c, "user");
                    Config.save();
                    chat.local("Last.fm account linked : " + Config.lastfmUser);
                    return 1;
                })));

        // owner settings
        cmd.then(ClientCommands.literal("unit").then(ClientCommands.argument("unit", StringArgumentType.word())
                .suggests(suggest(c -> List.of("cm", "inch")))
                .executes(c -> {
                    String u = StringArgumentType.getString(c, "unit").toLowerCase(Locale.ROOT);
                    if (!u.equals("cm") && !u.equals("inch")) {
                        chat.local("Use cm or inch.");
                        return 0;
                    }
                    Config.unit = u;
                    Config.save();
                    chat.local("!paff now uses " + u + ".");
                    return 1;
                })));
        cmd.then(ClientCommands.literal("mute").then(ClientCommands.argument("player", StringArgumentType.word())
                .executes(c -> {
                    String p = StringArgumentType.getString(c, "player");
                    if (!Text.isPlayerName(p)) {
                        chat.local("Invalid player name.");
                        return 0;
                    }
                    if (p.equalsIgnoreCase(Chat.ownerName())) {
                        chat.local("You cannot mute yourself.");
                        return 0;
                    }
                    Config.mute(p);
                    chat.local(p + " is muted : their commands are ignored.");
                    return 1;
                })));
        cmd.then(ClientCommands.literal("unmute").then(ClientCommands.argument("player", StringArgumentType.word())
                .suggests(suggest(c -> List.copyOf(Config.muted())))
                .executes(c -> {
                    String p = StringArgumentType.getString(c, "player");
                    chat.local(Config.unmute(p) ? p + " is no longer muted." : p + " was not muted.");
                    return 1;
                })));

        // custom commands
        cmd.then(ClientCommands.literal("test")
                .then(ClientCommands.argument("name", StringArgumentType.word())
                        .suggests(suggest(c -> List.copyOf(CustomCommands.all().keySet())))
                        .executes(c -> testCommand(chat, StringArgumentType.getString(c, "name"), ""))
                        .then(ClientCommands.argument("args", StringArgumentType.greedyString())
                                .executes(c -> testCommand(chat, StringArgumentType.getString(c, "name"),
                                        StringArgumentType.getString(c, "args"))))));
        cmd.then(ClientCommands.literal("editor")
                .executes(c -> {
                    ChatCmdsClient.openEditor = "";
                    return 1;
                })
                .then(ClientCommands.argument("name", StringArgumentType.word())
                        .suggests(suggest(c -> List.copyOf(CustomCommands.all().keySet())))
                        .executes(c -> {
                            ChatCmdsClient.openEditor = StringArgumentType.getString(c, "name").toLowerCase(Locale.ROOT);
                            return 1;
                        })));
        cmd.then(ClientCommands.literal("add").then(ClientCommands.argument("name", StringArgumentType.word())
                .then(ClientCommands.argument("response", StringArgumentType.greedyString()).executes(c -> {
                    String name = StringArgumentType.getString(c, "name");
                    String err = CustomCommands.add(name, StringArgumentType.getString(c, "response"));
                    if (err != null) {
                        chat.local(err);
                        return 0;
                    }
                    chat.local("Command !" + name.toLowerCase(Locale.ROOT).replaceFirst("^!", "") + " saved.");
                    return 1;
                }))));
        cmd.then(ClientCommands.literal("remove").then(ClientCommands.argument("name", StringArgumentType.word())
                .suggests(suggest(c -> List.copyOf(CustomCommands.all().keySet())))
                .executes(c -> {
                    String name = StringArgumentType.getString(c, "name");
                    chat.local(CustomCommands.remove(name) ? "Command !" + name + " removed."
                            : "No custom command named " + name + ".");
                    return 1;
                })));
        cmd.then(ClientCommands.literal("list").executes(c -> {
            Map<String, String> all = CustomCommands.all();
            if (all.isEmpty()) {
                chat.local("No custom command yet. Create one with /cmd add <name> <response>. "
                        + CustomCommands.HELP);
                return 1;
            }
            chat.local("Custom commands :");
            all.forEach((n, r) -> chat.local("!" + n + "  ->  " + r));
            return 1;
        }));

        // tools
        cmd.then(ClientCommands.literal("find").then(ClientCommands.argument("text", StringArgumentType.word())
                .executes(c -> {
                    String text = StringArgumentType.getString(c, "text");
                    Dispatcher.async(chat, () -> Hypixel.find(text, chat));
                    return 1;
                })));
        cmd.then(ClientCommands.literal("inflation").then(ClientCommands.literal("reset").executes(c -> {
            Dispatcher.async(chat, () -> Inflation.reset(chat));
            return 1;
        })));

        // /cmd censor ...
        LiteralArgumentBuilder<FabricClientCommandSource> censor = ClientCommands.literal("censor");
        censor.then(ClientCommands.literal("add").then(ClientCommands.argument("word", StringArgumentType.word())
                .executes(c -> {
                    String w = StringArgumentType.getString(c, "word");
                    if (w.length() < 2) {
                        chat.local("The word must have at least 2 letters.");
                        return 0;
                    }
                    Config.censorAdd(w);
                    chat.local("\"" + w + "\" will be sent as " + Text.censor(w, List.of(w)) + ".");
                    return 1;
                })));
        censor.then(ClientCommands.literal("remove").then(ClientCommands.argument("word", StringArgumentType.word())
                .suggests(suggest(c -> List.copyOf(Config.censored())))
                .executes(c -> {
                    String w = StringArgumentType.getString(c, "word");
                    chat.local(Config.censorRemove(w) ? "\"" + w + "\" is no longer masked."
                            : "\"" + w + "\" was not in the list.");
                    return 1;
                })));
        censor.then(ClientCommands.literal("list").executes(c -> {
            chat.local("Masked words : " + (Config.censored().isEmpty() ? "none"
                    : String.join(", ", Config.censored())));
            return 1;
        }));
        censor.then(ClientCommands.literal("defaults").executes(c -> {
            int n = Config.censorAddDefaults();
            chat.local(n + " word(s) added to the masked list. See them with /cmd censor list, "
                    + "remove one with /cmd censor remove <word>.");
            return 1;
        }));
        cmd.then(censor);

        // /cmd alias ...
        LiteralArgumentBuilder<FabricClientCommandSource> alias = ClientCommands.literal("alias");
        alias.then(ClientCommands.literal("list").executes(c -> {
            Map<String, String> all = Config.aliases();
            if (all.isEmpty()) {
                chat.local("No alias yet. Example : /cmd alias !p !pcookie");
                return 1;
            }
            for (Map.Entry<String, String> e : all.entrySet()) chat.local("!" + e.getKey() + " -> !" + e.getValue());
            return 1;
        }));
        alias.then(ClientCommands.literal("remove")
                .then(ClientCommands.argument("name", StringArgumentType.greedyString()).executes(c -> {
                    String n = StringArgumentType.getString(c, "name").trim().replaceFirst("^!", "");
                    chat.local(Config.aliasRemove(n) ? "Alias !" + n + " removed." : "No such alias.");
                    return 1;
                })));
        alias.then(ClientCommands.argument("spec", StringArgumentType.greedyString()).executes(c -> {
            // "<name> <command [args]>", with or without "!"
            String[] p = StringArgumentType.getString(c, "spec").trim().split("\\s+", 2);
            if (p.length < 2) {
                chat.local("Usage : /cmd alias <name> <command>   (example : /cmd alias !p !pcookie)");
                return 0;
            }
            String name = p[0].replaceFirst("^!", "").toLowerCase(Locale.ROOT);
            String target = p[1].trim().replaceFirst("^!", "");
            String targetCmd = target.split("\\s+")[0].toLowerCase(Locale.ROOT);
            if (!name.matches("[A-Za-z0-9_-]{1,20}")) {
                chat.local("The name must have 1 to 20 letters, digits, _ or -.");
                return 0;
            }
            if (BuiltIns.find(name) != null || CustomCommands.get(name) != null) {
                chat.local("!" + name + " already exists as a command.");
                return 0;
            }
            if (BuiltIns.find(targetCmd) == null && CustomCommands.get(targetCmd) == null) {
                chat.local("!" + targetCmd + " is not a command (aliases cannot point to another alias).");
                return 0;
            }
            Config.aliasSet(name, target);
            chat.local("Alias created : !" + name + " -> !" + target);
            return 1;
        }));
        cmd.then(alias);

        // /cmd rng ...
        LiteralArgumentBuilder<FabricClientCommandSource> rng = ClientCommands.literal("rng");
        SuggestionProvider<FabricClientCommandSource> dropList = suggest(c -> Rng.tokens());
        rng.then(ClientCommands.literal("select")
                .then(ClientCommands.argument("drop", StringArgumentType.word()).suggests(dropList)
                        .executes(c -> {
                            String d = StringArgumentType.getString(c, "drop");
                            chat.local(Rng.select(d) ? "Selected drop : " + d
                                    : "Unknown drop. Press Tab to list them.");
                            return 1;
                        })));
        rng.then(ClientCommands.literal("score")
                .then(ClientCommands.argument("category", StringArgumentType.word())
                        .suggests(suggest(c -> Rng.tokens().stream().map(t -> t.substring(0, t.indexOf('_')))
                                .distinct().toList()))
                        .then(ClientCommands.argument("value", LongArgumentType.longArg(0))
                                .executes(c -> {
                                    Rng.setScore(StringArgumentType.getString(c, "category").toLowerCase(Locale.ROOT),
                                            LongArgumentType.getLong(c, "value"));
                                    chat.local("Score saved.");
                                    return 1;
                                }))));
        cmd.then(rng);

        return cmd;
    }

    // ---------- helpers ----------

    private static int help(Chat chat) {
        chat.local("Chat Commands - /cmd <subcommand> :");
        chat.local("gui  status  reload  key <hypixel key>  lastfmkey <key>  link <lastfm user>");
        chat.local("unit <cm|inch>  mute <player>  unmute <player>");
        chat.local("add <name> <response>  remove <name>  list   (" + CustomCommands.HELP + ")");
        chat.local("editor [name]  test <name> [args]  censor add|remove|list|defaults  alias <name> <command>|list|remove  rng select|score  find <text>  inflation reset   |   also: /dh");
        return 1;
    }

    private static int status(Chat chat) {
        chat.local("Hypixel API key : " + Config.mask(Config.apiKey));
        chat.local("Last.fm API key : " + Config.mask(Config.lastfmKey)
                + ", account : " + (Config.lastfmUser.isEmpty() ? "not linked" : Config.lastfmUser));
        chat.local("!paff unit : " + Config.unit);
        chat.local("Masked words : " + (Config.censored().isEmpty() ? "none" : String.join(", ", Config.censored())));
        chat.local("Muted players : " + (Config.muted().isEmpty() ? "none" : String.join(", ", Config.muted())));
        chat.local("Custom commands : " + CustomCommands.all().size() + ", selected RNG drop : " + (Rng.selected().isEmpty() ? "none" : Rng.selected()));
        return 1;
    }

    /** /cmd test <name> [args] : runs a custom command with real data and shows the answer only to you. */
    private static int testCommand(Chat chat, String name, String args) {
        String template = CustomCommands.get(name);
        if (template == null) {
            chat.local("No custom command named " + name + ".");
            return 0;
        }
        String me = Chat.ownerName();
        CustomCommands.Ctx ctx = CustomCommands.Ctx.live(name.toLowerCase(Locale.ROOT), me == null ? "You" : me,
                me == null ? "You" : me, args);
        Dispatcher.async(chat, () -> {
            for (String line : CustomCommands.render(template, ctx).split("\\n")) {
                if (!line.isBlank()) chat.local("-> " + line);
            }
        });
        return 1;
    }

    private static SuggestionProvider<FabricClientCommandSource> suggest(
            Function<CommandContext<FabricClientCommandSource>, List<String>> source) {
        return (ctx, builder) -> {
            String typed = builder.getRemainingLowerCase();
            try {
                for (String s : source.apply(ctx)) {
                    if (s.toLowerCase(Locale.ROOT).startsWith(typed)) builder.suggest(s);
                }
            } catch (RuntimeException ignored) {
                // an argument is not typed yet: no suggestion
            }
            return builder.buildFuture();
        };
    }
}
