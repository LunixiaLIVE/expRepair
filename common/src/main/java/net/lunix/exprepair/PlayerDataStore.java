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
 * Replaces Fabric AttachmentType (NBT-based) storage.
 * Data is written to config/exprepair/playerdata.json.
 */
public class PlayerDataStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, PlayerEntry>>() {}.getType();

    private static Map<String, PlayerEntry> data = new HashMap<>();
    private static Path dataPath = null;

    public static class PlayerEntry {
        public boolean passivePermanent = false;
        public boolean manualPermanent  = false;
        public int     passiveThreshold = 0;
    }

    /** Must be called after SERVICES is set, so getConfigDir() is available. */
    public static void load() {
        dataPath = ExprepairCommon.SERVICES.getConfigDir()
                .resolve("exprepair").resolve("playerdata.json");
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
        if (dataPath == null) return;
        try {
            Files.createDirectories(dataPath.getParent());
            try (Writer writer = Files.newBufferedWriter(dataPath)) {
                GSON.toJson(data, MAP_TYPE, writer);
            }
        } catch (IOException e) {
            System.err.println("[exprepair] Failed to save player data: " + e.getMessage());
        }
    }

    /** Returns true if the player has an explicit entry in the file store. */
    public static boolean hasEntry(UUID uuid) {
        return data.containsKey(uuid.toString());
    }

    public static boolean isPassivePermanent(UUID uuid) {
        PlayerEntry entry = data.get(uuid.toString());
        return entry != null ? entry.passivePermanent : ExprepairCommon.defaultPassive;
    }

    public static void setPassivePermanent(UUID uuid, boolean value) {
        data.computeIfAbsent(uuid.toString(), k -> new PlayerEntry()).passivePermanent = value;
        save();
    }

    public static boolean isManualPermanent(UUID uuid) {
        PlayerEntry entry = data.get(uuid.toString());
        return entry != null ? entry.manualPermanent : ExprepairCommon.defaultManual;
    }

    public static void setManualPermanent(UUID uuid, boolean value) {
        data.computeIfAbsent(uuid.toString(), k -> new PlayerEntry()).manualPermanent = value;
        save();
    }

    public static int getPassiveThreshold(UUID uuid) {
        PlayerEntry entry = data.get(uuid.toString());
        return entry != null ? entry.passiveThreshold : ExprepairCommon.defaultThreshold;
    }

    public static void setPassiveThreshold(UUID uuid, int value) {
        data.computeIfAbsent(uuid.toString(), k -> new PlayerEntry()).passiveThreshold = value;
        save();
    }
}
