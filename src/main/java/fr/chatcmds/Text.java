package fr.chatcmds;

import java.util.Collection;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small text helpers (no Minecraft dependency). */
final class Text {
    static final int CHAT_MAX = 256;
    private static final Pattern COLOR_CODES = Pattern.compile("§.");
    private static final Pattern WORD = Pattern.compile("\\w{1,16}");

    private Text() {}

    /** Removes Minecraft color codes and trims. */
    static String plain(String raw) {
        return COLOR_CODES.matcher(raw).replaceAll("").trim();
    }

    /**
     * Makes a message safe to send in public chat:
     * - no control characters or color codes,
     * - never starts with "/" (would run a command) or "!" (would trigger bots, or loop on ourselves),
     * - at most 256 characters.
     */
    static String sanitize(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '§' || c < 32 || c == 127) continue;
            sb.append(c);
        }
        String m = sb.toString().replaceFirst("^[/!\\s]+", "").trim();
        if (m.length() > CHAT_MAX) m = m.substring(0, CHAT_MAX - 3) + "...";
        return m;
    }

    /**
     * Masks the given whole words (case-insensitive) with their first letter followed by asterisks,
     * for example "cocaine" becomes "c******". Used to get past the Hypixel chat filter.
     */
    static String censor(String s, Collection<String> words) {
        if (s == null) return "";
        String out = s;
        for (String w : words) {
            if (w == null || w.length() < 2) continue;
            Pattern p = Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(w) + "(?![\\p{L}\\p{N}])",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            out = p.matcher(out).replaceAll(m ->
                    Matcher.quoteReplacement(m.group().charAt(0) + "*".repeat(m.group().length() - 1)));
        }
        return out;
    }

    /** First word of an argument string if it looks like a Minecraft name, otherwise null. */
    static String firstWord(String args) {
        if (args == null || args.isBlank()) return null;
        String w = args.trim().split("\\s+")[0];
        return WORD.matcher(w).matches() ? w : null;
    }

    static boolean isPlayerName(String s) {
        return s != null && WORD.matcher(s).matches();
    }

    /** Item name typed after a command: letters, digits, space, ' and -, at most 40 characters. */
    static String itemQuery(String args) {
        if (args == null) return "";
        String s = args.replaceAll("[^\\p{L}\\p{N} '\\-]", "").replaceAll("\\s+", " ").trim();
        return s.length() > 40 ? s.substring(0, 40).trim() : s;
    }
}
