package fr.chatcmds;

import net.minecraft.client.Minecraft;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Minecraft side of {@link Out}: public chat and your own chat window. */
final class Chat implements Out {

    @Override
    public void say(String message) {
        String m = Text.sanitize(Text.censor(message, Config.censored()));
        if (m.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.getConnection() != null) mc.getConnection().sendChat(m);
        });
    }

    @Override
    public void local(String message) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player == null) return;
            ChatFormatting color = switch (Text.localKind(message)) {
                case 2 -> ChatFormatting.RED;
                case 1 -> ChatFormatting.YELLOW;
                default -> ChatFormatting.GREEN;
            };
            MutableComponent line = Component.literal("[chatcmds] ").withStyle(ChatFormatting.GOLD);
            line.append(Component.literal(message).withStyle(color));
            mc.player.sendSystemMessage(line);
        });
    }

    /** Runs a server command as you, without the leading slash (for example "warp dh"). */
    static void command(String command) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.getConnection() != null) mc.getConnection().sendCommand(command);
        });
    }

    /** Name of the logged-in player (the "owner" of the mod), or null when not in a world. */
    static String ownerName() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? null : mc.player.getName().getString();
    }
}
