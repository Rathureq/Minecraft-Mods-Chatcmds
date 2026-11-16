package fr.chatcmds;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Reads the song you are listening to from Last.fm (Spotify can scrobble to Last.fm). */
final class LastFm {
    private LastFm() {}

    static void nowPlaying(Out out) throws Exception {
        if (Config.lastfmKey.isEmpty()) {
            out.local("Last.fm API key missing. Create one on last.fm/api/account/create, then type /cmd lastfmkey <key>");
            return;
        }
        if (Config.lastfmUser.isEmpty()) {
            out.local("No Last.fm account linked. Type /cmd link <your Last.fm username>");
            return;
        }
        String url = "https://ws.audioscrobbler.com/2.0/?method=user.getrecenttracks&limit=1&format=json"
                + "&user=" + URLEncoder.encode(Config.lastfmUser, StandardCharsets.UTF_8)
                + "&api_key=" + URLEncoder.encode(Config.lastfmKey, StandardCharsets.UTF_8);
        JsonObject root = Http.getJson(url, null, null, "Last.fm").getAsJsonObject();
        out.say(describe(root));
    }

    /** Builds the chat reply from a user.getrecenttracks response. */
    static String describe(JsonObject root) {
        JsonObject recent = Json.obj(root, "recenttracks");
        JsonElement t = recent == null ? null : recent.get("track");
        JsonObject track = null;
        if (t != null && t.isJsonArray()) {
            JsonArray a = t.getAsJsonArray();
            if (a.size() > 0 && a.get(0).isJsonObject()) track = a.get(0).getAsJsonObject();
        } else if (t != null && t.isJsonObject()) {
            track = t.getAsJsonObject();
        }
        if (track == null) return "No track found on this Last.fm account.";

        String title = track.has("name") ? track.get("name").getAsString() : "?";
        JsonObject artistObj = Json.obj(track, "artist");
        String artist = (artistObj != null && artistObj.has("#text")) ? artistObj.get("#text").getAsString() : "?";
        JsonObject attr = Json.obj(track, "@attr");
        boolean now = attr != null && attr.has("nowplaying") && "true".equals(attr.get("nowplaying").getAsString());

        return now ? "Now playing : " + artist + " - " + title
                : "Nothing playing right now. Last track : " + artist + " - " + title;
    }
}
