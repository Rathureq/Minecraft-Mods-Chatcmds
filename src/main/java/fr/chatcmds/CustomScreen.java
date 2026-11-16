package fr.chatcmds;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** List of your custom commands: click one to edit it, or create a new one. Open with /cmd editor. */
final class CustomScreen extends Screen {
    private static final int ROW = 22;

    private final Screen parent;
    private final int requestedPage;
    private int page;
    private int pages;

    CustomScreen(Screen parent, int page) {
        super(Component.literal("Custom commands"));
        this.parent = parent;
        this.requestedPage = page;
    }

    @Override
    protected void init() {
        int w = this.width - 20;
        this.addRenderableWidget(Button.builder(Component.literal("+ New command"),
                        b -> this.minecraft.gui.setScreen(new EditorScreen(new CustomScreen(parent, 0), "")))
                .pos(10, 24).size(120, 20).build());

        List<String> names = new ArrayList<>(CustomCommands.all().keySet());
        int perPage = Math.max(1, (this.height - 100) / ROW);
        pages = Math.max(1, (names.size() + perPage - 1) / perPage);
        page = Math.min(Math.max(requestedPage, 0), pages - 1);

        Map<String, String> all = CustomCommands.all();
        int y = 52;
        for (int i = page * perPage; i < Math.min(names.size(), (page + 1) * perPage); i++) {
            String n = names.get(i);
            String label = fit("!" + n + "  -  " + all.get(n), w - 12);
            this.addRenderableWidget(Button.builder(Component.literal(label),
                            b -> this.minecraft.gui.setScreen(new EditorScreen(new CustomScreen(parent, page), n)))
                    .pos(10, y).size(w, 20).build());
            y += ROW;
        }

        Button prev = this.addRenderableWidget(Button.builder(Component.literal("< Prev"),
                        b -> this.minecraft.gui.setScreen(new CustomScreen(parent, page - 1)))
                .pos(this.width / 2 - 130, this.height - 28).size(80, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Back"), b -> this.onClose())
                .pos(this.width / 2 - 40, this.height - 28).size(80, 20).build());
        Button next = this.addRenderableWidget(Button.builder(Component.literal("Next >"),
                        b -> this.minecraft.gui.setScreen(new CustomScreen(parent, page + 1)))
                .pos(this.width / 2 + 50, this.height - 28).size(80, 20).build());
        prev.active = page > 0;
        next.active = page < pages - 1;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.text(this.font, "Custom commands  (page " + (page + 1) + "/" + pages + ")", 10, 10, 0xFFFFFF55);
        if (CustomCommands.all().isEmpty()) {
            graphics.text(this.font, "None yet. Click \"+ New command\".", 10, 60, 0xFFFFFFFF);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }

    private String fit(String s, int maxWidth) {
        if (this.font.width(s) <= maxWidth) return s;
        String t = s;
        while (t.length() > 1 && this.font.width(t + "...") > maxWidth) t = t.substring(0, t.length() - 1);
        return t + "...";
    }
}
