package fr.chatcmds;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Read-only list of all the commands. Open it with /cmd gui. */
final class CommandsScreen extends Screen {
    private record Line(String text, int color) {}

    private static final int TITLE = 0xFFFFFF55;
    private static final int HEADER = 0xFF55FFFF;
    private static final int NORMAL = 0xFFFFFFFF;
    private static final int OWNER = 0xFFFFAA00;
    private static final int CUSTOM = 0xFF55FF55;
    private static final int LINE_HEIGHT = 12;

    private final int requestedPage;
    private int page;
    private int pages;
    private int perPage;
    private List<Line> lines = List.of();

    CommandsScreen(int page) {
        super(Component.literal("Chat Commands"));
        this.requestedPage = page;
    }

    @Override
    protected void init() {
        lines = buildLines();
        perPage = Math.max(1, (this.height - 92) / LINE_HEIGHT);
        pages = Math.max(1, (lines.size() + perPage - 1) / perPage);
        page = Math.min(Math.max(requestedPage, 0), pages - 1);

        Button prev = this.addRenderableWidget(Button.builder(Component.literal("< Prev"),
                        b -> this.minecraft.gui.setScreen(new CommandsScreen(page - 1)))
                .pos(this.width / 2 - 130, this.height - 28)
                .size(80, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("Close"), b -> this.onClose())
                .pos(this.width / 2 - 40, this.height - 28)
                .size(80, 20)
                .build());
        Button next = this.addRenderableWidget(Button.builder(Component.literal("Next >"),
                        b -> this.minecraft.gui.setScreen(new CommandsScreen(page + 1)))
                .pos(this.width / 2 + 50, this.height - 28)
                .size(80, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("Edit custom commands"),
                        b -> this.minecraft.gui.setScreen(new CustomScreen(this, 0)))
                .pos(this.width / 2 - 80, this.height - 52)
                .size(160, 20)
                .build());
        prev.active = page > 0;
        next.active = page < pages - 1;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // super draws the background (blurred once) and the buttons: do not draw the background twice.
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        graphics.text(this.font, "Chat Commands  (page " + (page + 1) + "/" + pages + ")", 10, 10, TITLE);
        graphics.text(this.font, "Orange = only you can use it. Green = created with /cmd add.", 10, 24, NORMAL);

        int maxWidth = this.width - 20;
        int y = 42;
        int from = page * perPage;
        int to = Math.min(lines.size(), from + perPage);
        for (int i = from; i < to; i++) {
            Line l = lines.get(i);
            graphics.text(this.font, fit(l.text(), maxWidth), 10, y, l.color());
            y += LINE_HEIGHT;
        }
    }

    /** Cuts a line that is wider than the screen. */
    private String fit(String s, int maxWidth) {
        if (this.font.width(s) <= maxWidth) return s;
        String t = s;
        while (t.length() > 1 && this.font.width(t + "...") > maxWidth) t = t.substring(0, t.length() - 1);
        return t + "...";
    }

    private static List<Line> buildLines() {
        List<Line> out = new ArrayList<>();
        out.add(new Line("-- Chat commands (type them in the chat) --", HEADER));
        for (BuiltIns.Cmd c : BuiltIns.ALL) {
            out.add(new Line("!" + c.label() + "  -  " + c.description(), c.ownerOnly() ? OWNER : NORMAL));
        }
        out.add(new Line("-- Your custom commands --", HEADER));
        var custom = CustomCommands.all();
        if (custom.isEmpty()) {
            out.add(new Line("none yet : /cmd add <name> <response>", NORMAL));
        } else {
            custom.forEach((n, r) -> out.add(new Line("!" + n + "  -  " + r, CUSTOM)));
        }
        out.add(new Line("-- Slash commands (only on your computer) --", HEADER));
        out.add(new Line("/dh  -  /warp dh", NORMAL));
        out.add(new Line("/rng [drop]  -  RNG meter fill, Tab completes the drops", NORMAL));
        out.add(new Line("/cmd help  -  keys, accounts, mute, custom commands, aliases, editor", NORMAL));
        return out;
    }
}
