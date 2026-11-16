package fr.chatcmds;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Random;

/**
 * Test bench of a custom command: choose who writes it and what they type, run it, and read the answer
 * without sending anything to the chat. Opened with the Test button of the editor.
 */
final class TestScreen extends Screen {
    private final Screen parent;
    private final String command;
    private final String response;
    private String sender;
    private String args = "Alex Bob Carl";
    private volatile List<String> output = List.of();
    private volatile String status = "";
    private EditBox senderBox;
    private EditBox argsBox;

    TestScreen(Screen parent, String command, String response) {
        super(Component.literal("Test"));
        this.parent = parent;
        this.command = command.isBlank() ? "test" : command;
        this.response = response;
        String me = Chat.ownerName();
        this.sender = me == null ? "Steve" : me;
    }

    @Override
    protected void init() {
        senderBox = new EditBox(this.font, 10, 46, 150, 18, Component.literal("Sender"));
        senderBox.setMaxLength(16);
        senderBox.setValue(sender);
        senderBox.setResponder(v -> sender = v);
        this.addRenderableWidget(senderBox);

        argsBox = new EditBox(this.font, 10, 86, this.width - 20, 18, Component.literal("Arguments"));
        argsBox.setMaxLength(100);
        argsBox.setValue(args);
        argsBox.setResponder(v -> args = v);
        this.addRenderableWidget(argsBox);

        this.addRenderableWidget(Button.builder(Component.literal("Run (sample data)"), b -> runSample())
                .pos(10, 112).size(130, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Run live"), b -> runLive())
                .pos(146, 112).size(90, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Back"), b -> this.onClose())
                .pos(this.width / 2 - 40, this.height - 28).size(80, 20).build());
    }

    private void show(String text) {
        output = text.lines().filter(l -> !l.isBlank()).limit(CustomCommands.MAX_LINES).toList();
    }

    /** Fixed sample values and no network: instant. */
    private void runSample() {
        String owner = Chat.ownerName();
        CustomCommands.Ctx ctx = new CustomCommands.Ctx(command, sender, owner == null ? sender : owner,
                false, args, new Random(), true);
        status = "Sample data : prices, area and counters are fake.";
        show(CustomCommands.render(response, ctx));
    }

    /** Real prices, area, counters and stored variables (read only). Nothing is sent, nothing is counted. */
    private void runLive() {
        status = "Running...";
        output = List.of();
        String owner = Chat.ownerName();
        CustomCommands.Ctx ctx = CustomCommands.Ctx.live(command, sender, owner == null ? sender : owner, args);
        Thread t = new Thread(() -> {
            try {
                show(CustomCommands.render(response, ctx));
                status = "Live result. Nothing was sent to the chat.";
            } catch (RuntimeException e) {
                status = "Error : " + e.getMessage();
            }
        }, "chatcmds-test");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.text(this.font, "Test !" + command + "  (nothing is sent to the chat)", 10, 10, 0xFFFFFF55);
        graphics.text(this.font, "Who writes the command ({user})", 10, 34, 0xFFAAAAAA);
        graphics.text(this.font, "What they type after the command ({arg} {arg2} {args})", 10, 74, 0xFFAAAAAA);
        int y = 142;
        graphics.text(this.font, fit(status, this.width - 20), 10, y, 0xFF888888);
        y += 14;
        int n = 1;
        for (String line : output) {
            graphics.text(this.font, fit("Message " + n + " : " + line, this.width - 20), 10, y, 0xFF55FF55);
            y += 12;
            n++;
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
