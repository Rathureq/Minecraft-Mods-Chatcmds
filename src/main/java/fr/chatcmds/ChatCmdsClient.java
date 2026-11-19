package fr.chatcmds;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.nio.file.Path;

/**
 * Chat Commands : answers "!command" typed in the chat by any player.
 * See README.md for the list of commands and the setup.
 */
public class ChatCmdsClient implements ClientModInitializer {
    /** Set by /cmd gui, handled on the next tick (a screen opened during the command would be closed). */
    static volatile boolean openGui;
    /** Set by /cmd editor: "" opens the list of custom commands, a name opens that command. */
    static volatile String openEditor;
    private static long ticks;

    @Override
    public void onInitializeClient() {
        Path dir = FabricLoader.getInstance().getConfigDir();
        Config.init(dir);
        Config.load();
        CustomCommands.init(dir);
        Rng.init(dir);
        Usage.init(dir);
        Inflation.init(dir);
        Config.uuidSupplier = () -> {
            Minecraft mc = Minecraft.getInstance();
            return mc.player == null ? null : mc.player.getUUID().toString();
        };

        Chat chat = new Chat();
        Dispatcher dispatcher = new Dispatcher(chat, Chat::ownerName);

        // Hypixel sends player messages as "game" messages.
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) dispatcher.onMessage(message.getString());
        });
        // Hides the /locraw answer that the {area} placeholder asked for.
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) ->
                overlay || !Area.consume(message.getString()));
        // Vanilla servers: signed player messages.
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, timestamp) ->
                dispatcher.onMessage(message.getString()));

        ClientCommandRegistrationCallback.EVENT.register((cmdDispatcher, buildContext) ->
                CmdCommands.register(cmdDispatcher, chat));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openGui) {
                openGui = false;
                client.gui.setScreen(new CommandsScreen(0));
            }
            String editor = openEditor;
            if (editor != null) {
                openEditor = null;
                client.gui.setScreen(editor.isEmpty() ? new CustomScreen(null, 0)
                        : new EditorScreen(new CustomScreen(null, 0), editor));
            }
            ticks++;
            // Silent inflation measure every 30 minutes (first one after 1 minute), only while in a world.
            if (ticks % 36000 == 1200 && client.player != null) {
                Dispatcher.async(chat, Inflation::snapshot);
            }
        });
    }
}
