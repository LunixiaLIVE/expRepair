package net.lunix.exprepair.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.lunix.exprepair.ExprepairCommon;
import net.lunix.exprepair.PlayerDataStore;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Fabric entry point — wires Fabric events to the platform-agnostic common logic.
 */
public class ExprepairFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        // Hand the Fabric implementation to common code
        ExprepairCommon.init(new FabricPlatformServices());

        // Lifecycle
        ServerLifecycleEvents.SERVER_STARTED.register(ExprepairCommon::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ExprepairCommon.onServerStopped());

        // Tick
        ServerTickEvents.END_SERVER_TICK.register(ExprepairCommon::onServerTick);

        // Connections
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            UUID uuid = player.getUUID();

            // Migrate legacy NBT attachment data to the file-based store (one-time, on first join)
            if (!PlayerDataStore.hasEntry(uuid)) {
                boolean legacyPassive = player.getAttachedOrElse(FabricPlatformServices.PASSIVE_PERMANENT, ExprepairCommon.defaultPassive);
                boolean legacyManual  = player.getAttachedOrElse(FabricPlatformServices.MANUAL_PERMANENT,  ExprepairCommon.defaultManual);
                int     legacyThresh  = player.getAttachedOrElse(FabricPlatformServices.PASSIVE_THRESHOLD, ExprepairCommon.defaultThreshold);
                if (legacyPassive != ExprepairCommon.defaultPassive) PlayerDataStore.setPassivePermanent(uuid, legacyPassive);
                if (legacyManual  != ExprepairCommon.defaultManual)  PlayerDataStore.setManualPermanent(uuid, legacyManual);
                if (legacyThresh  != ExprepairCommon.defaultThreshold) PlayerDataStore.setPassiveThreshold(uuid, legacyThresh);
            }

            // Remove legacy NBT data entirely so it no longer accumulates in player NBT
            player.setAttached(FabricPlatformServices.PASSIVE_PERMANENT, null);
            player.setAttached(FabricPlatformServices.MANUAL_PERMANENT, null);
            player.setAttached(FabricPlatformServices.PASSIVE_THRESHOLD, null);

            ExprepairCommon.onPlayerJoin(player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
            ExprepairCommon.onPlayerDisconnect(handler.player.getUUID()));

        // Item use (manual repair)
        UseItemCallback.EVENT.register(ExprepairCommon::onUseItem);

        // Commands
        CommandRegistrationCallback.EVENT.register(ExprepairCommon::registerCommands);
    }
}
