package net.lunix.exprepair.fabric;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.loader.api.FabricLoader;
import net.lunix.exprepair.ExprepairCommon;
import net.lunix.exprepair.PlayerDataStore;
import net.lunix.exprepair.PlatformServices;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;

/**
 * Fabric implementation of PlatformServices.
 * Per-player persistent data is now stored in config/exprepair/playerdata.json
 * via PlayerDataStore. The AttachmentType fields below are kept only as legacy
 * hooks so the JOIN migration handler can read and clear old NBT data.
 */
public class FabricPlatformServices implements PlatformServices {

    // Legacy — kept only for migrating existing NBT data
    static final AttachmentType<Boolean> PASSIVE_PERMANENT = AttachmentRegistry.create(
        Identifier.fromNamespaceAndPath("exprepair", "passive_permanent"),
        builder -> builder.persistent(Codec.BOOL).initializer(() -> ExprepairCommon.defaultPassive));

    // Legacy — kept only for migrating existing NBT data
    static final AttachmentType<Boolean> MANUAL_PERMANENT = AttachmentRegistry.create(
        Identifier.fromNamespaceAndPath("exprepair", "manual_permanent"),
        builder -> builder.persistent(Codec.BOOL).initializer(() -> ExprepairCommon.defaultManual));

    // Legacy — kept only for migrating existing NBT data
    static final AttachmentType<Integer> PASSIVE_THRESHOLD = AttachmentRegistry.create(
        Identifier.fromNamespaceAndPath("exprepair", "passive_threshold"),
        builder -> builder.persistent(Codec.INT).initializer(() -> ExprepairCommon.defaultThreshold));

    // -------------------------------------------------------------------------

    @Override
    public Path getConfigDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public String getModVersion() {
        return FabricLoader.getInstance()
            .getModContainer("exprepair")
            .map(c -> c.getMetadata().getVersion().getFriendlyString())
            .orElse("unknown");
    }

    // -------------------------------------------------------------------------
    // Per-player persistent data — backed by PlayerDataStore (file-based)
    // -------------------------------------------------------------------------

    @Override
    public boolean getPassivePermanent(ServerPlayer player) {
        return PlayerDataStore.isPassivePermanent(player.getUUID());
    }

    @Override
    public void setPassivePermanent(ServerPlayer player, boolean value) {
        PlayerDataStore.setPassivePermanent(player.getUUID(), value);
    }

    @Override
    public boolean getManualPermanent(ServerPlayer player) {
        return PlayerDataStore.isManualPermanent(player.getUUID());
    }

    @Override
    public void setManualPermanent(ServerPlayer player, boolean value) {
        PlayerDataStore.setManualPermanent(player.getUUID(), value);
    }

    @Override
    public int getPassiveThreshold(ServerPlayer player) {
        return PlayerDataStore.getPassiveThreshold(player.getUUID());
    }

    @Override
    public void setPassiveThreshold(ServerPlayer player, int value) {
        PlayerDataStore.setPassiveThreshold(player.getUUID(), value);
    }

    @Override
    public boolean hasAdminPermission(CommandSourceStack source) {
        return source.hasPermission(2);
    }
}
