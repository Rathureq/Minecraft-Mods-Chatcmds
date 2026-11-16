package fr.chatcmds;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Editor of one custom command: name, response, buttons that insert placeholders, live preview.
 * Open with /cmd editor [name] or from /cmd gui.
 */
final class EditorScreen extends Screen {
    /** {button label, text inserted in the response} */
    private static final String[][] SNIPPETS = {
            {"user", "{user}"}, {"me", "{me}"}, {"owner", "{owner}"}, {"arg", "{arg}"}, {"arg2", "{arg2}"},
            {"args", "{args}"}, {"target", "{target}"}, {"random", "{random:1-100}"}, {"percent", "{percent}"},
            {"pick", "{pick:a|b|c}"}, {"time", "{time}"}, {"date", "{date}"}, {"count", "{count}"},
            {"mycount", "{mycount}"}, {"top", "{top}"}, {"math", "{math:2*{arg}}"}, {"price", "{price:cookie}"},
            {"sell", "{sell:cookie}"}, {"replace", "{replace:{arg}|a|4}"}, {"set", "{set:x=5}"},
            {"get", "{get:x|0}"}, {"store", "{store:x=5}"}, {"load", "{load:x|0}"},
            {"inc", "{inc:x}"}, {"sinc", "{sinc:x}"}, {"area", "{area}"}, {"br", "{br}"},
            {"until", "{until:2026-12-25}"}, {"if", "{if:{arg}==?then|else}"}, {"elif", "|elif:{arg}==?then"}, {"else", "|else:text"},
    };

    private final Screen parent;
    /** Name of the command being edited, or null when creating a new one. */
    private final String original;
    private String nameText;
    private String responseText;
    private String message = "";
    private boolean messageOk;
    private EditBox nameBox;
    private EditBox responseBox;

    EditorScreen(Screen parent, String name) {
        super(Component.literal("Edit command"));
        this.parent = parent;
        String existing = name.isEmpty() ? null : CustomCommands.get(name);
        this.original = existing == null ? null : name;
        this.nameText = name;
        this.responseText = existing == null ? "" : existing;
    }

    @Override
    protected void init() {
        nameBox = new EditBox(this.font, 10, 46, 150, 18, Component.literal("Name"));
        nameBox.setMaxLength(20);
        nameBox.setValue(nameText);
        nameBox.setResponder(v -> nameText = v);
        this.addRenderableWidget(nameBox);

        responseBox = new EditBox(this.font, 10, 86, this.width - 20, 18, Component.literal("Response"));
        responseBox.setMaxLength(200);
        responseBox.setValue(responseText);
        responseBox.setResponder(v -> responseText = v);
        this.addRenderableWidget(responseBox);

        int bw = 58;
        int cols = Math.max(1, (this.width - 20) / (bw + 2));
        for (int i = 0; i < SNIPPETS.length; i++) {
            final String insert = SNIPPETS[i][1];
            int x = 10 + (i % cols) * (bw + 2);
            int y = 150 + (i / cols) * 18;
            this.addRenderableWidget(Button.builder(Component.literal(SNIPPETS[i][0]),
                            b -> responseBox.insertText(insert))
                    .pos(x, y).size(bw, 16).build());
        }

        int by = this.height - 28;
        int bx = this.width / 2 - 152;
        this.addRenderableWidget(Button.builder(Component.literal("Save"), b -> save())
                .pos(bx, by).size(72, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Test"), b -> test())
                .pos(bx + 76, by).size(72, 20).build());
        Button delete = this.addRenderableWidget(Button.builder(Component.literal("Delete"), b -> delete())
                .pos(bx + 152, by).size(72, 20).build());
        delete.active = original != null;
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> this.onClose())
                .pos(bx + 228, by).size(72, 20).build());
    }

    private void test() {
        String err = CustomCommands.validate(responseText);
        if (err != null) {
            message = err;
            messageOk = false;
            return;
        }
        String n = nameText.trim().toLowerCase(Locale.ROOT).replaceFirst("^!", "");
        this.minecraft.gui.setScreen(new TestScreen(this, n, responseText));
    }

    private void save() {
        String n = nameText.trim().toLowerCase(Locale.ROOT).replaceFirst("^!", "");
        String err = CustomCommands.add(n, responseText);
        if (err != null) {
            message = err;
            messageOk = false;
            return;
        }
        if (original != null && !original.equals(n)) CustomCommands.remove(original);
        this.minecraft.gui.setScreen(parent);
    }

    private void delete() {
        if (original != null) CustomCommands.remove(original);
        this.minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.text(this.font, original == null ? "New command" : "Edit !" + original, 10, 10, 0xFFFFFF55);
        graphics.text(this.font, "Name (people type !name in the chat)", 10, 34, 0xFFAAAAAA);
        graphics.text(this.font, "Response (click the buttons below to insert placeholders)", 10, 74, 0xFFAAAAAA);

        String live = CustomCommands.validate(responseText);
        if (live == null) {
            graphics.text(this.font, fit("Preview : " + CustomCommands.preview(responseText, Chat.ownerName()),
                    this.width - 20), 10, 112, 0xFF55FF55);
        } else if (!responseText.isBlank()) {
            graphics.text(this.font, fit(live, this.width - 20), 10, 112, 0xFFFF5555);
        }
        graphics.text(this.font, "Preview uses sample data : user Steve, arguments \"Alex Bob Carl\".", 10, 124, 0xFF888888);
        if (!message.isEmpty()) {
            graphics.text(this.font, fit(message, this.width - 20), 10, 136, messageOk ? 0xFF55FF55 : 0xFFFF5555);
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
