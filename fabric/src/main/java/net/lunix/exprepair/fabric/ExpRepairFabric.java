package net.lunix.exprepair.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.lunix.exprepair.Exprepair;

public class ExpRepairFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        String version = FabricLoader.getInstance().getModContainer(Exprepair.MOD_ID)
            .map(c -> c.getMetadata().getVersion().getFriendlyString())
            .orElse("unknown");
        Exprepair.setup(FabricLoader.getInstance().getConfigDir(), version);

        ServerLifecycleEvents.SERVER_STARTED.register(Exprepair::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> Exprepair.onServerStopped());
        ServerTickEvents.END_SERVER_TICK.register(Exprepair::onServerTick);
        UseItemCallback.EVENT.register(Exprepair::onUseItem);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> Exprepair.onPlayerJoin(handler.player));
        CommandRegistrationCallback.EVENT.register(
            (dispatcher, registryAccess, environment) -> Exprepair.registerCommands(dispatcher));
    }
}
