package net.lunix.exprepair.neoforge;

import net.lunix.exprepair.Exprepair;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod(Exprepair.MOD_ID)
public class ExpRepairNeoForge {

    public ExpRepairNeoForge(IEventBus modBus) {
        String version = ModList.get().getModContainerById(Exprepair.MOD_ID)
            .map(c -> c.getModInfo().getVersion().toString())
            .orElse("unknown");
        Exprepair.setup(FMLPaths.CONFIGDIR.get(), version);
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        Exprepair.onServerStarted(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        Exprepair.onServerStopped();
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        Exprepair.onServerTick(event.getServer());
    }

    @SubscribeEvent
    public void onUseItem(PlayerInteractEvent.RightClickItem event) {
        InteractionResult result = Exprepair.onUseItem(event.getEntity(), event.getLevel(), event.getHand());
        if (result != InteractionResult.PASS) {
            event.setCanceled(true);
            event.setCancellationResult(result);
        }
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            Exprepair.onPlayerJoin(serverPlayer);
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        Exprepair.registerCommands(event.getDispatcher());
    }
}
