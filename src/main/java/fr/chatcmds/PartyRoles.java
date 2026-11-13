package fr.chatcmds;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Remembers the class of the players who join a dungeon group through Party Finder.
 * Read from the chat only (no API call about other players).
 *
 * Expected line: "Party Finder > Name joined the dungeon group! (Mage Level 40)"
 */
final class PartyRoles {
    private static final Pattern JOIN = Pattern.compile(
            "^Party Finder > (?:\\[[^\\]]+\\] ?)*(\\w{1,16}) joined the dungeon group! \\((\\w+) Level (\\d+)\\)$");
    private static final Pattern LEFT = Pattern.compile(
            "^(?:\\[[^\\]]+\\] ?)*(\\w{1,16}) (?:has left the party|was removed from the party|has been removed from the party).*$");

    private static final Map<String, String> MEMBERS = new LinkedHashMap<>();

    private PartyRoles() {}

    static synchronized void observe(String text) {
        Matcher j = JOIN.matcher(text);
        if (j.matches()) {
            MEMBERS.put(j.group(1), j.group(2) + " " + j.group(3));
            return;
        }
        Matcher l = LEFT.matcher(text);
        if (l.matches()) {
            MEMBERS.remove(l.group(1));
            return;
        }
        if (text.startsWith("You left the party")
                || text.startsWith("You are not currently in a party")
                || text.startsWith("The party was disbanded")
                || text.contains("has disbanded the party")
                || text.startsWith("You have been kicked from the party")) {
            MEMBERS.clear();
        }
    }

    static synchronized String summary() {
        if (MEMBERS.isEmpty()) {
            return "No Party Finder member recorded yet (players are recorded when they join through Party Finder).";
        }
        StringBuilder sb = new StringBuilder();
        for (var e : MEMBERS.entrySet()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(e.getKey()).append(": ").append(e.getValue());
        }
        return sb.toString();
    }

    static synchronized void clear() {
        MEMBERS.clear();
    }
}
