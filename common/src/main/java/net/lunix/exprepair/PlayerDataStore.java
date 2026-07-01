package net.lunix.exprepair;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * File-based per-player persistent data store.
 * Data is written to config/exprepair/playerdata.json.
 */
public class PlayerDataStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path dataPath;
    private static final Type MAP_TYPE = new TypeToken<Map<String, PlayerEntry>>() {}.getType();

    /** Set by Exprepair.setup() before load()/save() are used. */
    public static void init(Path configDir) {
        dataPath = configDir.resolve("exprepair").resolve("playerdata.json");
    }

    private static Map<String, PlayerEntry> data = new HashMap<>();

    public static class PlayerEntry {
        public boolean passivePermanent = false;
        public boolean manualPermanent  = false;
        public int     passiveThreshold = 0;
        public boolean loginMessage     = true;
    }

    public static void load() {
        if (Files.exists(dataPath)) {
            try (Reader reader = Files.newBufferedReader(dataPath)) {
                Map<String, PlayerEntry> loaded = GSON.fromJson(reader, MAP_TYPE);
                if (loaded != null) data = loaded;
            } catch (IOException e) {
                System.err.println("[exprepair] Failed to load player data: " + e.getMessage());
            }
        }
    }

    public static void save() {
        try {
            Files.createDirectories(dataPath.getParent());
            try (Writer writer = Files.newBufferedWriter(dataPath)) {
                GSON.toJson(data, MAP_TYPE, writer);
            }
        } catch (IOException e) {
            System.err.println("[exprepair] Failed to save player data: " + e.getMessage());
        }
    }

    public static boolean isPassivePermanent(UUID uuid) {
        PlayerEntry entry = data.get(uuid.toString());
        return entry != null ? entry.passivePermanent : Exprepair.defaultPassive;
    }

    public static void setPassivePermanent(UUID uuid, boolean value) {
        data.computeIfAbsent(uuid.toString(), k -> new PlayerEntry()).passivePermanent = value;
        save();
    }

    public static boolean isManualPermanent(UUID uuid) {
        PlayerEntry entry = data.get(uuid.toString());
        return entry != null ? entry.manualPermanent : Exprepair.defaultManual;
    }

    public static void setManualPermanent(UUID uuid, boolean value) {
        data.computeIfAbsent(uuid.toString(), k -> new PlayerEntry()).manualPermanent = value;
        save();
    }

    public static int getPassiveThreshold(UUID uuid) {
        PlayerEntry entry = data.get(uuid.toString());
        return entry != null ? entry.passiveThreshold : Exprepair.defaultThreshold;
    }

    public static void setPassiveThreshold(UUID uuid, int value) {
        data.computeIfAbsent(uuid.toString(), k -> new PlayerEntry()).passiveThreshold = value;
        save();
    }

    public static boolean isLoginMessageEnabled(UUID uuid) {
        PlayerEntry entry = data.get(uuid.toString());
        return entry == null || entry.loginMessage;
    }

    public static void setLoginMessageEnabled(UUID uuid, boolean value) {
        data.computeIfAbsent(uuid.toString(), k -> new PlayerEntry()).loginMessage = value;
        save();
    }
}
