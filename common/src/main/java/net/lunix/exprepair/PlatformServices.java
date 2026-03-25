package net.lunix.exprepair;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;

/**
 * Platform abstraction layer.
 * Each loader (Fabric, NeoForge, etc.) provides its own implementation.
 * Common code calls through this interface instead of using loader-specific APIs directly.
 */
public interface PlatformServices {

    /** Returns the directory where config files should be stored. */
    Path getConfigDir();

    /** Returns the mod's current version string. */
    String getModVersion();

    // -------------------------------------------------------------------------
    // Per-player persistent data (replaces Fabric AttachmentRegistry calls)
    // -------------------------------------------------------------------------

    boolean getPassivePermanent(ServerPlayer player);
    void    setPassivePermanent(ServerPlayer player, boolean value);

    boolean getManualPermanent(ServerPlayer player);
    void    setManualPermanent(ServerPlayer player, boolean value);

    int  getPassiveThreshold(ServerPlayer player);
    void setPassiveThreshold(ServerPlayer player, int value);

    /** Returns true if the given command source has admin (OP level 2+) permissions. */
    boolean hasAdminPermission(CommandSourceStack source);
}
