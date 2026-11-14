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
 *   /rng <category> <option> <drop>      -> same as !rng, with tab completion
 *   /cc ...                              -> settings, custom commands, GUI
 */
final class CcCommands {
    private CcCommands() {}

    static void register(CommandDispatcher<FabricClientCommandSource> d, Chat chat) {
        d.register(ClientCommands.literal("dh").executes(c -> {
            Chat.command("warp dh");
            return 1;
        }));
        d.register(rngCommand(chat));
        d.register(ccCommand(chat));
    }

    // ---------- /rng ----------

    private static LiteralArgumentBuilder<FabricClientCommandSource> rngCommand(Chat chat) {
        SuggestionProvider<FabricClientCommandSource> cats = suggest(c -> Rng.categories());
        SuggestionProvider<FabricClientCommandSource> opts = suggest(c ->
                Rng.options(StringArgumentType.getString(c, "category")));
        SuggestionProvider<FabricClientCommandSource> drops = suggest(c ->
                Rng.drops(StringArgumentType.getString(c, "category"), StringArgumentType.getString(c, "option")));

        return ClientCommands.literal("rng")
                .then(ClientCommands.argument("category", StringArgumentType.word()).suggests(cats)
                        .then(ClientCommands.argument("option", StringArgumentType.word()).suggests(opts)
                                .then(ClientCommands.argument("drop", StringArgumentType.word()).suggests(drops)
                                        .executes(c -> {
                                            String cat = StringArgumentType.getString(c, "category");
                                            String opt = StringArgumentType.getString(c, "option");
                                            String drop = StringArgumentType.getString(c, "drop");
                                            Dispatcher.async(chat, () -> Rng.run(cat, opt, drop, chat));
                                            return 1;
                                        }))));
    }

    // ---------- /cc ----------

    private static LiteralArgumentBuilder<FabricClientCommandSource> ccCommand(Chat chat) {
        LiteralArgumentBuilder<FabricClientCommandSource> cc = ClientCommands.literal("cc");

        cc.executes(c -> help(chat));
        cc.then(ClientCommands.literal("help").executes(c -> help(chat)));
        cc.then(ClientCommands.literal("gui").executes(c -> {
            ChatCmdsClient.openGui = true;
            return 1;
        }));
        cc.then(ClientCommands.literal("status").executes(c -> status(chat)));
        cc.then(ClientCommands.literal("reload").executes(c -> {
            Config.load();
            CustomCommands.load();
            Rng.load();
            Hypixel.invalidate();
            chat.local("Config reloaded.");
            return 1;
        }));

        // keys and accounts
        cc.then(ClientCommands.literal("key").then(ClientCommands.argument("key", StringArgumentType.word())
                .executes(c -> {
                    Config.apiKey = StringArgumentType.getString(c, "key");
                    Config.save();
                    Hypixel.invalidate();
                    chat.local("Hypixel API key saved " + Config.mask(Config.apiKey) + ". Try !sblvl.");
                    return 1;
                })));
        cc.then(ClientCommands.literal("lastfmkey").then(ClientCommands.argument("key", StringArgumentType.word())
                .executes(c -> {
                    Config.lastfmKey = StringArgumentType.getString(c, "key");
                    Config.save();
                    chat.local("Last.fm API key saved " + Config.mask(Config.lastfmKey) + ".");
                    return 1;
                })));
        cc.then(ClientCommands.literal("link").then(ClientCommands.argument("user", StringArgumentType.word())
                .executes(c -> {
                    Config.lastfmUser = StringArgumentType.getString(c, "user");
                    Config.save();
                    chat.local("Last.fm account linked : " + Config.lastfmUser);
                    return 1;
                })));

        // owner settings
        cc.then(ClientCommands.literal("unit").then(ClientCommands.argument("unit", StringArgumentType.word())
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
        cc.then(ClientCommands.literal("mute").then(ClientCommands.argument("player", StringArgumentType.word())
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
        cc.then(ClientCommands.literal("unmute").then(ClientCommands.argument("player", StringArgumentType.word())
                .suggests(suggest(c -> List.copyOf(Config.muted())))
                .executes(c -> {
                    String p = StringArgumentType.getString(c, "player");
                    chat.local(Config.unmute(p) ? p + " is no longer muted." : p + " was not muted.");
                    return 1;
                })));

        // custom commands
        cc.then(ClientCommands.literal("add").then(ClientCommands.argument("name", StringArgumentType.word())
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
        cc.then(ClientCommands.literal("remove").then(ClientCommands.argument("name", StringArgumentType.word())
                .suggests(suggest(c -> List.copyOf(CustomCommands.all().keySet())))
                .executes(c -> {
                    String name = StringArgumentType.getString(c, "name");
                    chat.local(CustomCommands.remove(name) ? "Command !" + name + " removed."
                            : "No custom command named " + name + ".");
                    return 1;
                })));
        cc.then(ClientCommands.literal("list").executes(c -> {
            Map<String, String> all = CustomCommands.all();
            if (all.isEmpty()) {
                chat.local("No custom command yet. Create one with /cc add <name> <response>. "
                        + CustomCommands.HELP);
                return 1;
            }
            chat.local("Custom commands :");
            all.forEach((n, r) -> chat.local("!" + n + "  ->  " + r));
            return 1;
        }));

        // tools
        cc.then(ClientCommands.literal("find").then(ClientCommands.argument("text", StringArgumentType.word())
                .executes(c -> {
                    String text = StringArgumentType.getString(c, "text");
                    Dispatcher.async(chat, () -> Hypixel.find(text, chat));
                    return 1;
                })));
        cc.then(ClientCommands.literal("inflation").then(ClientCommands.literal("reset").executes(c -> {
            Dispatcher.async(chat, () -> Inflation.reset(chat));
            return 1;
        })));

        // /cc censor ...
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
            chat.local(n + " word(s) added to the masked list. See them with /cc censor list, "
                    + "remove one with /cc censor remove <word>.");
            return 1;
        }));
        cc.then(censor);

        // /cc rng ...
        LiteralArgumentBuilder<FabricClientCommandSource> rng = ClientCommands.literal("rng");
        rng.then(ClientCommands.literal("list").executes(c -> {
            List<Rng.Entry> all = Rng.all();
            if (all.isEmpty()) {
                chat.local("No RNG entry yet. Add one with /cc rng add <category> <option> <drop> <required> <path>");
                return 1;
            }
            for (Rng.Entry e : all) {
                chat.local(e.category() + " " + e.option() + " " + e.drop() + "  (" + e.required()
                        + " req, path " + e.path() + ")");
            }
            return 1;
        }));
        rng.then(ClientCommands.literal("add")
                .then(ClientCommands.argument("category", StringArgumentType.word())
                        .then(ClientCommands.argument("option", StringArgumentType.word())
                                .then(ClientCommands.argument("drop", StringArgumentType.word())
                                        .then(ClientCommands.argument("required", LongArgumentType.longArg(1))
                                                .then(ClientCommands.argument("path", StringArgumentType.word())
                                                        .executes(c -> {
                                                            Rng.add(StringArgumentType.getString(c, "category"),
                                                                    StringArgumentType.getString(c, "option"),
                                                                    StringArgumentType.getString(c, "drop"),
                                                                    LongArgumentType.getLong(c, "required"),
                                                                    StringArgumentType.getString(c, "path"));
                                                            chat.local("RNG entry saved.");
                                                            return 1;
                                                        })))))));
        rng.then(ClientCommands.literal("remove")
                .then(ClientCommands.argument("category", StringArgumentType.word())
                        .then(ClientCommands.argument("option", StringArgumentType.word())
                                .then(ClientCommands.argument("drop", StringArgumentType.word())
                                        .executes(c -> {
                                            boolean ok = Rng.remove(StringArgumentType.getString(c, "category"),
                                                    StringArgumentType.getString(c, "option"),
                                                    StringArgumentType.getString(c, "drop"));
                                            chat.local(ok ? "RNG entry removed." : "No such RNG entry.");
                                            return 1;
                                        })))));
        cc.then(rng);

        return cc;
    }

    // ---------- helpers ----------

    private static int help(Chat chat) {
        chat.local("Chat Commands - /cc <subcommand> :");
        chat.local("gui  status  reload  key <hypixel key>  lastfmkey <key>  link <lastfm user>");
        chat.local("unit <cm|inch>  mute <player>  unmute <player>");
        chat.local("add <name> <response>  remove <name>  list   (" + CustomCommands.HELP + ")");
        chat.local("censor add|remove|list|defaults  rng list|add|remove  find <text>  inflation reset   |   also: /dh  /rng");
        return 1;
    }

    private static int status(Chat chat) {
        chat.local("Hypixel API key : " + Config.mask(Config.apiKey));
        chat.local("Last.fm API key : " + Config.mask(Config.lastfmKey)
                + ", account : " + (Config.lastfmUser.isEmpty() ? "not linked" : Config.lastfmUser));
        chat.local("!paff unit : " + Config.unit);
        chat.local("Masked words : " + (Config.censored().isEmpty() ? "none" : String.join(", ", Config.censored())));
        chat.local("Muted players : " + (Config.muted().isEmpty() ? "none" : String.join(", ", Config.muted())));
        chat.local("Custom commands : " + CustomCommands.all().size() + ", RNG entries : " + Rng.all().size());
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
