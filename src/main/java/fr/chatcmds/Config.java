package fr.chatcmds;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/** Fichier de config : .minecraft/config/chatcmds.json */
public class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("chatcmds.json");

    /** Clé API Hypixel (https://developer.hypixel.net) — nécessaire pour !comp1 à !comp7. */
    public static String apiKey = "";
    /** Ton UUID (optionnel). Vide = UUID du joueur connecté. */
    public static String uuid = "";
    /** Diviseur de l'indice !inflation (0 = somme des poids). */
    public static double inflationDivisor = 0;

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                JsonObject o = GSON.fromJson(Files.readString(FILE), JsonObject.class);
                if (o != null) {
                    if (o.has("api_key")) apiKey = o.get("api_key").getAsString().trim();
                    if (o.has("uuid")) uuid = o.get("uuid").getAsString().trim();
                    if (o.has("inflation_divisor")) inflationDivisor = o.get("inflation_divisor").getAsDouble();
                }
            } else {
                JsonObject o = new JsonObject();
                o.addProperty("api_key", "");
                o.addProperty("uuid", "");
                o.addProperty("inflation_divisor", 0);
                Files.createDirectories(FILE.getParent());
                Files.writeString(FILE, GSON.toJson(o));
            }
        } catch (Exception e) {
            System.err.println("[chatcmds] Impossible de lire la config : " + e);
        }
    }
}
