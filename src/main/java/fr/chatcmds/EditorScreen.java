package fr.chatcmds;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Editor of one custom command: name, response, and a toolbar of placeholders sorted in tabs.
 * Click a button to insert it where the cursor is; hover it (or click it) to read what it does,
 * with the result it gives on sample data. Open with /cmd editor [name] or from /cmd gui.
 */
final class EditorScreen extends Screen {
    /** A toolbar button. When replace is true, the text replaces the whole response (examples). */
    private record Snip(String label, String text, String description, boolean replace) {}

    private static Snip s(String label, String text, String description) {
        return new Snip(label, text, description, false);
    }

    private static Snip ex(String label, String text, String description) {
        return new Snip(label, text, description, true);
    }

    private static final String[] TAB_NAMES = {"Sender", "Words", "Random", "Info", "Text", "Variables", "If", "Examples"};

    private static final Snip[][] TABS = {
            { // Sender
                    s("user", "{user}", "Name of the player who typed the command."),
                    s("me", "{me}", "Your own name."),
                    s("owner", "{owner}", "true when you typed the command, false for anybody else. Use it in an If."),
            },
            { // Words typed after the command
                    s("arg", "{arg}", "First word typed after the command. Empty if nothing was typed."),
                    s("arg2", "{arg2}", "Second word typed after the command."),
                    s("arg3", "{arg3}", "Third word typed after the command."),
                    s("args", "{args}", "Everything typed after the command, all the words."),
                    s("target", "{target}", "First word typed, or the sender's name when nothing was typed."),
            },
            { // Random
                    s("random", "{random:1-100}", "Random whole number between the two values, both included."),
                    s("percent", "{percent}", "Random percentage, for example 57%."),
                    s("pick", "{pick:a|b|c}", "One random choice among the options. Separate the options with |."),
            },
            { // Info
                    s("time", "{time}", "Current time, like 21:35."),
                    s("date", "{date}", "Today's date, like 2026-11-16."),
                    s("until", "{until:2026-12-25}", "Days left until a date written year-month-day. Negative when the date is past."),
                    s("area", "{area}", "Where you are in SkyBlock (Hub, Dungeon...). Asked to the server, takes a moment."),
                    s("count", "{count}", "How many times this command was used, by everybody."),
                    s("mycount", "{mycount}", "How many times the sender used this command."),
                    s("top", "{top}", "The 3 players who used this command the most."),
                    s("price", "{price:cookie}", "Bazaar insta buy price of an item, like !pprice. Takes a moment."),
                    s("sell", "{sell:cookie}", "Bazaar insta sell price of an item. Takes a moment."),
            },
            { // Text and numbers
                    s("math", "{math:2+3*4}", "Calculator, gives only the result. Supports + - * / % ^ and parentheses. Can use {arg} or variables."),
                    s("replace", "{replace:{arg}|e|3}", "Replaces every e by 3 in the text. Form: {replace:text|from|to}."),
                    s("new line", "{br}", "New line: the answer is sent as several chat messages, 1.2 s apart (5 at most)."),
            },
            { // Variables
                    s("set", "{set:x=5}", "Creates the variable x with the value 5. Write it before you use x."),
                    s("get", "{get:x|0}", "Value of x. The text after | is used when x does not exist. {x} is the same."),
                    s("inc", "{inc:x}", "Adds 1 to x. {inc:x=5} adds 5, {inc:x=-1} removes 1. Gives no text."),
                    s("store", "{store:x=5}", "Saves x for the next answers, shared by all your commands."),
                    s("load", "{load:x|0}", "Reads a saved variable. {get:x} also finds it when x is not set here."),
                    s("sinc", "{sinc:x}", "Adds 1 to a saved variable, like a counter that never resets."),
            },
            { // Conditions
                    s("if", "{if:{arg}==?then|else}", "If A is true: then, otherwise: else. Tests > < >= <= == !=. Numbers or text."),
                    s("elif", "|elif:{arg}==?then", "Another test after an If: write it just before the last else."),
                    s("else", "|else:text", "What to say when no test was true. Optional."),
            },
            { // Full examples
                    ex("Dice", "{user} rolled {random:1-6}", "Replaces the response with an example: a die."),
                    ex("Coin", "{if:{random:1-2}==1?Heads|Tails}", "Heads or tails, with an If."),
                    ex("Greeting", "{if:{arg}==?Hello everyone|Hello {arg}}", "Different answer when a name is given."),
                    ex("Counter", "{sinc:visits}Visit #{load:visits}", "A counter that survives restarts."),
                    ex("Grade", "{if:{arg}>=16?Great|elif:{arg}>=10?Pass|Fail}", "Three answers depending on a number."),
                    ex("Rules", "Rule 1: be nice{br}Rule 2: have fun", "Two chat messages with {br}."),
                    ex("Countdown", "Christmas in {until:2026-12-25} days", "Days left until a date."),
                    ex("Roll twice", "{set:a={random:1-6}}{set:b={random:1-6}}{a} + {b} = {math:{a}+{b}}",
                            "Variables: two dice added together."),
            },
    };

    private final Screen parent;
    /** Name of the command being edited, or null when creating a new one. */
    private final String original;
    private String nameText;
    private String responseText;
    private String message;
    private boolean messageOk;
    private int tab;
    /** Button whose description is shown when nothing is hovered (the last one clicked), or null. */
    private Snip selected;
    private EditBox nameBox;
    private EditBox responseBox;
    private final List<int[]> rects = new ArrayList<>();
    private final List<Snip> rectSnips = new ArrayList<>();

    EditorScreen(Screen parent, String name) {
        super(Component.literal("Edit command"));
        this.parent = parent;
        String existing = name.isEmpty() ? null : CustomCommands.get(name);
        this.original = existing == null ? null : name;
        this.nameText = name;
        this.responseText = existing == null ? "" : existing;
        this.message = "";
    }

    /** Same screen, different tab (the screen is rebuilt, the typed text is kept). */
    private EditorScreen(EditorScreen from, int tab, Snip selected) {
        super(Component.literal("Edit command"));
        this.parent = from.parent;
        this.original = from.original;
        this.nameText = from.nameText;
        this.responseText = from.responseText;
        this.message = from.message;
        this.messageOk = from.messageOk;
        this.tab = tab;
        this.selected = selected;
    }

    @Override
    protected void init() {
        rects.clear();
        rectSnips.clear();

        nameBox = new EditBox(this.font, 10, 38, 150, 18, Component.literal("Name"));
        nameBox.setMaxLength(20);
        nameBox.setValue(nameText);
        nameBox.setResponder(v -> nameText = v);
        this.addRenderableWidget(nameBox);

        responseBox = new EditBox(this.font, 10, 70, this.width - 20, 18, Component.literal("Response"));
        responseBox.setMaxLength(200);
        responseBox.setValue(responseText);
        responseBox.setResponder(v -> responseText = v);
        this.addRenderableWidget(responseBox);

        // tabs
        int x = 10;
        for (int i = 0; i < TAB_NAMES.length; i++) {
            final int index = i;
            int w = this.font.width(TAB_NAMES[i]) + 12;
            String label = i == tab ? "[" + TAB_NAMES[i] + "]" : TAB_NAMES[i];
            this.addRenderableWidget(Button.builder(Component.literal(label),
                            b -> this.minecraft.gui.setScreen(new EditorScreen(this, index, null)))
                    .pos(x, 122).size(w + (i == tab ? 8 : 0), 16).build());
            x += w + (i == tab ? 8 : 0) + 2;
        }

        // toolbar of the current tab
        int bx = 10;
        int by = 142;
        for (Snip sn : TABS[tab]) {
            int w = Math.max(40, this.font.width(sn.label()) + 14);
            if (bx + w > this.width - 10) {
                bx = 10;
                by += 18;
            }
            this.addRenderableWidget(Button.builder(Component.literal(sn.label()), b -> click(sn))
                    .pos(bx, by).size(w, 16).build());
            rects.add(new int[] {bx, by, w, 16});
            rectSnips.add(sn);
            bx += w + 2;
        }

        int bottom = this.height - 26;
        int left = this.width / 2 - 152;
        this.addRenderableWidget(Button.builder(Component.literal("Save"), b -> save())
                .pos(left, bottom).size(72, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Test"), b -> test())
                .pos(left + 76, bottom).size(72, 20).build());
        Button delete = this.addRenderableWidget(Button.builder(Component.literal("Delete"), b -> delete())
                .pos(left + 152, bottom).size(72, 20).build());
        delete.active = original != null;
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> this.onClose())
                .pos(left + 228, bottom).size(72, 20).build());
    }

    private void click(Snip sn) {
        selected = sn;
        if (sn.replace()) {
            responseBox.setValue(sn.text());
            if (nameText.isBlank()) {
                nameBox.setValue(sn.label().toLowerCase(Locale.ROOT).replace(' ', '-'));
            }
        } else {
            responseBox.insertText(sn.text());
        }
    }

    private void save() {
        String n = nameText.trim().toLowerCase(Locale.ROOT).replaceFirst("^!", "");
        String err = CustomCommands.add(n, responseText);
        if (err != null) {
            message = err;
            messageOk = false;
            this.minecraft.gui.setScreen(new EditorScreen(this, tab, selected));
            return;
        }
        if (original != null && !original.equals(n)) CustomCommands.remove(original);
        this.minecraft.gui.setScreen(parent);
    }

    private void test() {
        String err = CustomCommands.validate(responseText);
        if (err != null) {
            message = err;
            messageOk = false;
            this.minecraft.gui.setScreen(new EditorScreen(this, tab, selected));
            return;
        }
        String n = nameText.trim().toLowerCase(Locale.ROOT).replaceFirst("^!", "");
        this.minecraft.gui.setScreen(new TestScreen(this, n, responseText));
    }

    private void delete() {
        if (original != null) CustomCommands.remove(original);
        this.minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.text(this.font, original == null ? "New command" : "Edit !" + original, 10, 10, 0xFFFFFF55);
        graphics.text(this.font, "Name : people type !name in the chat", 10, 28, 0xFFAAAAAA);
        graphics.text(this.font, "Response : click the buttons below to insert placeholders", 10, 60, 0xFFAAAAAA);

        String live = CustomCommands.validate(responseText);
        if (live == null) {
            graphics.text(this.font, fit("Preview : " + CustomCommands.preview(responseText, Chat.ownerName()),
                    this.width - 20), 10, 94, 0xFF55FF55);
        } else if (!responseText.isBlank()) {
            graphics.text(this.font, fit(live, this.width - 20), 10, 94, 0xFFFF5555);
        } else {
            graphics.text(this.font, "Preview uses sample data : user Steve, words \"Alex Bob Carl\".", 10, 94, 0xFF888888);
        }
        if (!message.isEmpty()) {
            graphics.text(this.font, fit(message, this.width - 20), 10, 106, messageOk ? 0xFF55FF55 : 0xFFFF5555);
        }

        // description of the hovered button, else of the last clicked one
        Snip shown = selected;
        for (int i = 0; i < rects.size(); i++) {
            int[] r = rects.get(i);
            if (mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + r[3]) {
                shown = rectSnips.get(i);
                break;
            }
        }
        int y = this.height - 62;
        if (shown == null) {
            graphics.text(this.font, "Hover a button to see what it does. Click it to insert it at the cursor.",
                    10, y, 0xFF888888);
            return;
        }
        String text = shown.text();
        graphics.text(this.font, fit(text, this.width - 20), 10, y, 0xFFFFFF55);
        String result = CustomCommands.preview(text, Chat.ownerName());
        if (!shown.replace() && !text.startsWith("|") && !result.isEmpty() && !result.equals(text)) {
            int w = this.font.width(fit(text, this.width - 20)) + 8;
            if (w < this.width - 80) {
                graphics.text(this.font, fit("-> " + result, this.width - 20 - w), 10 + w, y, 0xFF55FF55);
            }
        } else if (shown.replace() && !result.isEmpty()) {
            graphics.text(this.font, fit("-> " + result, this.width - 20), 10, y + 11, 0xFF55FF55);
            y += 11;
        }
        for (String line : wrap(shown.description(), this.width - 20, 2)) {
            y += 11;
            graphics.text(this.font, line, 10, y, 0xFFFFFFFF);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }

    private List<String> wrap(String s, int maxWidth, int maxLines) {
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : s.split(" ")) {
            if (cur.length() > 0 && this.font.width(cur + " " + word) > maxWidth) {
                lines.add(cur.toString());
                cur = new StringBuilder();
            }
            if (cur.length() > 0) cur.append(' ');
            cur.append(word);
        }
        if (cur.length() > 0) lines.add(cur.toString());
        return lines.size() > maxLines ? lines.subList(0, maxLines) : lines;
    }

    private String fit(String s, int maxWidth) {
        if (this.font.width(s) <= maxWidth) return s;
        String t = s;
        while (t.length() > 1 && this.font.width(t + "...") > maxWidth) t = t.substring(0, t.length() - 1);
        return t + "...";
    }
}
