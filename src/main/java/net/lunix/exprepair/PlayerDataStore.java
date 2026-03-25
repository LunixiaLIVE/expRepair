package net.lunix.exprepair;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

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
    private static final Path DATA_PATH = FabricLoader.getInstance().getConfigDir()
            .resolve("exprepair").resolve("playerdata.json");
    private static final Type MAP_TYPE = new TypeToken<Map<String, PlayerEntry>>() {}.getType();

    private static Map<String, PlayerEntry> data = new HashMap<>();

    public static class PlayerEntry {
        public boolean passivePermanent = false;
        public boolean manualPermanent  = false;
        public int     passiveThreshold = 0;
    }

    public static void load() {
        if (Files.exists(DATA_PATH)) {
            try (Reader reader = Files.newBufferedReader(DATA_PATH)) {
                Map<String, PlayerEntry> loaded = GSON.fromJson(reader, MAP_TYPE);
                if (loaded != null) data = loaded;
            } catch (IOException e) {
                System.err.println("[exprepair] Failed to load player data: " + e.getMessage());
            }
        }
    }

    public static void save() {
        try {
            Files.createDirectories(DATA_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(DATA_PATH)) {
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
}
