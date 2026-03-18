package net.lunix.exprepair;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Exprepair implements ModInitializer {

    // --- Server defaults (loaded from config; used as attachment initializers) ---
    static boolean defaultPassive   = false;
    static boolean defaultManual    = false;
    static int     defaultThreshold = 0;
    static int     maxXpPerRepair   = 8;

    // --- Server-wide allow flags; if false, players cannot enable that mode ---
    static boolean allowPassive     = true;
    static boolean allowManual      = true;

    // --- Per-player persistent attachments ---
    public static final AttachmentType<Boolean> PASSIVE_PERMANENT = AttachmentRegistry.create(
        Identifier.fromNamespaceAndPath("exprepair", "passive_permanent"),
        builder -> builder.persistent(Codec.BOOL).initializer(() -> defaultPassive));

    public static final AttachmentType<Integer> PASSIVE_THRESHOLD = AttachmentRegistry.create(
        Identifier.fromNamespaceAndPath("exprepair", "passive_threshold"),
        builder -> builder.persistent(Codec.INT).initializer(() -> defaultThreshold));

    public static final AttachmentType<Boolean> MANUAL_PERMANENT = AttachmentRegistry.create(
        Identifier.fromNamespaceAndPath("exprepair", "manual_permanent"),
        builder -> builder.persistent(Codec.BOOL).initializer(() -> defaultManual));

    // In-memory session overrides; cleared on disconnect.
    private static final Map<UUID, Boolean> PASSIVE_SESSION = new HashMap<>();
    private static final Map<UUID, Boolean> MANUAL_SESSION  = new HashMap<>();

    private static Holder<Enchantment> mendingEntry = null;

    private static final EquipmentSlot[] SLOTS = {
        EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
        EquipmentSlot.HEAD, EquipmentSlot.CHEST,
        EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    // =========================================================================
    // Init
    // =========================================================================

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            loadConfig();
            mendingEntry = server.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.MENDING);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> mendingEntry = null);
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID uuid = handler.player.getUUID();
            PASSIVE_SESSION.remove(uuid);
            MANUAL_SESSION.remove(uuid);
        });
        UseItemCallback.EVENT.register(this::onUseItem);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
            sendLoginMessage(handler.player));
        registerCommands();
    }

    // =========================================================================
    // Manual repair
    // =========================================================================

    private InteractionResult onUseItem(Player player, Level world, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (world.isClientSide()) return InteractionResult.PASS;

        ServerPlayer serverPlayer = (ServerPlayer) player;
        if (!isEligible(serverPlayer)) return InteractionResult.PASS;
        if (!player.isShiftKeyDown() || stack.isEmpty() || !stack.isDamaged()) return InteractionResult.PASS;
        if (!allowManual || !isEffective(player, MANUAL_PERMANENT, MANUAL_SESSION)) return InteractionResult.PASS;
        if (mendingEntry == null || stack.getEnchantments().getLevel(mendingEntry) == 0) return InteractionResult.PASS;

        int availableXp = Math.min(maxXpPerRepair, getTotalXp(player));
        if (availableXp <= 0) {
            player.displayClientMessage(Component.literal("Not enough XP to repair.").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }

        int damage   = stack.getDamageValue();
        int xpNeeded = (damage + 1) / 2;
        int xpToUse  = Math.min(availableXp, xpNeeded);
        stack.setDamageValue(Math.max(0, damage - xpToUse * 2));
        drainXp(player, xpToUse);

        String msg = stack.getDamageValue() == 0
            ? "Item fully repaired."
            : "Partially repaired (" + (xpToUse * 2) + " durability).";
        player.displayClientMessage(Component.literal(msg).withStyle(ChatFormatting.GREEN), true);
        return InteractionResult.SUCCESS;
    }

    // =========================================================================
    // Passive repair
    // =========================================================================

    private void onServerTick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0 || mendingEntry == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!isEligible(player)) continue;
            if (!allowPassive || !isEffective(player, PASSIVE_PERMANENT, PASSIVE_SESSION)) continue;
            repairEquippedItems(player);
        }
    }

    private static void repairEquippedItems(ServerPlayer player) {
        int threshold   = player.getAttachedOrElse(PASSIVE_THRESHOLD, 0);
        int reservedXp  = getXpToReachLevel(threshold);
        int availableXp = Math.min(maxXpPerRepair, Math.max(0, getTotalXp(player) - reservedXp));
        if (availableXp <= 0) return;

        int xpUsed = 0;
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty() || !stack.isDamaged()) continue;
            if (stack.getEnchantments().getLevel(mendingEntry) == 0) continue;
            int remainingXp = availableXp - xpUsed;
            if (remainingXp <= 0) break;
            int damage   = stack.getDamageValue();
            int xpNeeded = (damage + 1) / 2;
            int xpToUse  = Math.min(remainingXp, xpNeeded);
            stack.setDamageValue(Math.max(0, damage - xpToUse * 2));
            xpUsed += xpToUse;
        }
        if (xpUsed > 0) drainXp(player, xpUsed);
    }

    // =========================================================================
    // Login message
    // =========================================================================

    private static void sendLoginMessage(ServerPlayer player) {
        boolean passivePerm = player.getAttachedOrElse(PASSIVE_PERMANENT, defaultPassive);
        boolean manualPerm  = player.getAttachedOrElse(MANUAL_PERMANENT,  defaultManual);
        boolean passiveOn   = passivePerm && allowPassive;
        boolean manualOn    = manualPerm  && allowManual;
        int threshold       = player.getAttachedOrElse(PASSIVE_THRESHOLD, defaultThreshold);

        player.displayClientMessage(header("expRepair", "XP-based mending for your gear"), false);

        MutableComponent passiveStatus = allowPassive
            ? statusLabel(passivePerm).append(Component.literal("  "))
                .append(suggestButton("configure", "/exprepair passive",
                    "Open passive repair options\n/exprepair passive session\n/exprepair passive permanent"))
            : Component.literal("DISABLED").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal(" (admin)").withStyle(ChatFormatting.GRAY));
        player.displayClientMessage(
            Component.literal("  Passive repair: ").withStyle(ChatFormatting.GRAY).append(passiveStatus), false);

        MutableComponent manualStatus = allowManual
            ? statusLabel(manualPerm).append(Component.literal("  "))
                .append(suggestButton("configure", "/exprepair manual",
                    "Open manual repair options\n/exprepair manual session\n/exprepair manual permanent"))
            : Component.literal("DISABLED").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal(" (admin)").withStyle(ChatFormatting.GRAY));
        player.displayClientMessage(
            Component.literal("  Manual repair:  ").withStyle(ChatFormatting.GRAY).append(manualStatus), false);

        if (passiveOn) {
            String threshStr = threshold == 0 ? "none" : threshold + " levels";
            player.displayClientMessage(
                Component.literal("  XP threshold:   ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(threshStr)
                        .withStyle(threshold == 0 ? ChatFormatting.YELLOW : ChatFormatting.AQUA))
                    .append(Component.literal("  "))
                    .append(suggestButton("change", "/exprepair threshold ",
                        "Set min XP levels passive repair won't spend below\n"
                        + "Usage: /exprepair threshold <levels>\nUse 0 to clear")), false);
        }

        MutableComponent tip = Component.literal("  ");
        if (manualOn) {
            tip.append(Component.literal("Sneak + right-click in air to manually repair.  ")
                .withStyle(ChatFormatting.GRAY));
        }
        tip.append(runButton("? help", "/exprepair", "View all expRepair commands"));
        player.displayClientMessage(tip, false);
    }

    // =========================================================================
    // Command registration
    // =========================================================================

    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                Commands.literal("exprepair")
                    .executes(ctx -> showHelp(ctx.getSource()))

                    // Player commands
                    .then(Commands.literal("passive")
                        .executes(ctx -> showOptions(ctx.getSource(), "Passive repair", "passive",
                            PASSIVE_PERMANENT, PASSIVE_SESSION))
                        .then(Commands.literal("session")
                            .executes(ctx -> toggleFeature(ctx.getSource(), "Passive repair", "passive",
                                PASSIVE_PERMANENT, PASSIVE_SESSION, MANUAL_PERMANENT, MANUAL_SESSION, true)))
                        .then(Commands.literal("permanent")
                            .executes(ctx -> toggleFeature(ctx.getSource(), "Passive repair", "passive",
                                PASSIVE_PERMANENT, PASSIVE_SESSION, MANUAL_PERMANENT, MANUAL_SESSION, false)))
                    )
                    .then(Commands.literal("manual")
                        .executes(ctx -> showOptions(ctx.getSource(), "Manual repair", "manual",
                            MANUAL_PERMANENT, MANUAL_SESSION))
                        .then(Commands.literal("session")
                            .executes(ctx -> toggleFeature(ctx.getSource(), "Manual repair", "manual",
                                MANUAL_PERMANENT, MANUAL_SESSION, PASSIVE_PERMANENT, PASSIVE_SESSION, true)))
                        .then(Commands.literal("permanent")
                            .executes(ctx -> toggleFeature(ctx.getSource(), "Manual repair", "manual",
                                MANUAL_PERMANENT, MANUAL_SESSION, PASSIVE_PERMANENT, PASSIVE_SESSION, false)))
                    )
                    .then(Commands.literal("threshold")
                        .executes(ctx -> showThreshold(ctx.getSource()))
                        .then(Commands.argument("levels", IntegerArgumentType.integer(0))
                            .executes(ctx -> setThreshold(ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "levels"))))
                    )

                    // Admin: per-player
                    .then(Commands.literal("admin")
                        .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .then(Commands.argument("target", EntityArgument.player())
                            .executes(ctx -> adminStatus(ctx.getSource(),
                                EntityArgument.getPlayer(ctx, "target")))
                            .then(Commands.literal("status")
                                .executes(ctx -> adminStatus(ctx.getSource(),
                                    EntityArgument.getPlayer(ctx, "target"))))
                            .then(Commands.literal("reset")
                                .executes(ctx -> adminReset(ctx.getSource(),
                                    EntityArgument.getPlayer(ctx, "target"))))
                            .then(Commands.literal("passive")
                                .then(Commands.literal("session")
                                    .then(Commands.literal("on")
                                        .executes(ctx -> adminSetPassive(ctx.getSource(),
                                            EntityArgument.getPlayer(ctx, "target"), true, true)))
                                    .then(Commands.literal("off")
                                        .executes(ctx -> adminSetPassive(ctx.getSource(),
                                            EntityArgument.getPlayer(ctx, "target"), true, false)))
                                )
                                .then(Commands.literal("permanent")
                                    .then(Commands.literal("on")
                                        .executes(ctx -> adminSetPassive(ctx.getSource(),
                                            EntityArgument.getPlayer(ctx, "target"), false, true)))
                                    .then(Commands.literal("off")
                                        .executes(ctx -> adminSetPassive(ctx.getSource(),
                                            EntityArgument.getPlayer(ctx, "target"), false, false)))
                                )
                            )
                            .then(Commands.literal("manual")
                                .then(Commands.literal("session")
                                    .then(Commands.literal("on")
                                        .executes(ctx -> adminSetManual(ctx.getSource(),
                                            EntityArgument.getPlayer(ctx, "target"), true, true)))
                                    .then(Commands.literal("off")
                                        .executes(ctx -> adminSetManual(ctx.getSource(),
                                            EntityArgument.getPlayer(ctx, "target"), true, false)))
                                )
                                .then(Commands.literal("permanent")
                                    .then(Commands.literal("on")
                                        .executes(ctx -> adminSetManual(ctx.getSource(),
                                            EntityArgument.getPlayer(ctx, "target"), false, true)))
                                    .then(Commands.literal("off")
                                        .executes(ctx -> adminSetManual(ctx.getSource(),
                                            EntityArgument.getPlayer(ctx, "target"), false, false)))
                                )
                            )
                            .then(Commands.literal("threshold")
                                .then(Commands.argument("levels", IntegerArgumentType.integer(0))
                                    .executes(ctx -> adminSetThreshold(ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "target"),
                                        IntegerArgumentType.getInteger(ctx, "levels"))))
                            )
                        )
                    )

                    // Admin: server defaults
                    .then(Commands.literal("default")
                        .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .executes(ctx -> showDefaults(ctx.getSource()))
                        .then(Commands.literal("passive")
                            .then(Commands.literal("on")
                                .executes(ctx -> setDefaultPassive(ctx.getSource(), true)))
                            .then(Commands.literal("off")
                                .executes(ctx -> setDefaultPassive(ctx.getSource(), false)))
                            .then(Commands.literal("allow")
                                .then(Commands.literal("on")
                                    .executes(ctx -> setAllowPassive(ctx.getSource(), true, true))
                                    .then(Commands.literal("broadcast")
                                        .executes(ctx -> setAllowPassive(ctx.getSource(), true, true)))
                                    .then(Commands.literal("silent")
                                        .executes(ctx -> setAllowPassive(ctx.getSource(), true, false)))
                                )
                                .then(Commands.literal("off")
                                    .executes(ctx -> setAllowPassive(ctx.getSource(), false, true))
                                    .then(Commands.literal("broadcast")
                                        .executes(ctx -> setAllowPassive(ctx.getSource(), false, true)))
                                    .then(Commands.literal("silent")
                                        .executes(ctx -> setAllowPassive(ctx.getSource(), false, false)))
                                )
                            )
                        )
                        .then(Commands.literal("manual")
                            .then(Commands.literal("on")
                                .executes(ctx -> setDefaultManual(ctx.getSource(), true)))
                            .then(Commands.literal("off")
                                .executes(ctx -> setDefaultManual(ctx.getSource(), false)))
                            .then(Commands.literal("allow")
                                .then(Commands.literal("on")
                                    .executes(ctx -> setAllowManual(ctx.getSource(), true, true))
                                    .then(Commands.literal("broadcast")
                                        .executes(ctx -> setAllowManual(ctx.getSource(), true, true)))
                                    .then(Commands.literal("silent")
                                        .executes(ctx -> setAllowManual(ctx.getSource(), true, false)))
                                )
                                .then(Commands.literal("off")
                                    .executes(ctx -> setAllowManual(ctx.getSource(), false, true))
                                    .then(Commands.literal("broadcast")
                                        .executes(ctx -> setAllowManual(ctx.getSource(), false, true)))
                                    .then(Commands.literal("silent")
                                        .executes(ctx -> setAllowManual(ctx.getSource(), false, false)))
                                )
                            )
                        )
                        .then(Commands.literal("threshold")
                            .then(Commands.argument("levels", IntegerArgumentType.integer(0))
                                .executes(ctx -> setDefaultThreshold(ctx.getSource(),
                                    IntegerArgumentType.getInteger(ctx, "levels"))))
                        )
                        .then(Commands.literal("maxXpPerRepair")
                            .then(Commands.argument("xp", IntegerArgumentType.integer(1))
                                .executes(ctx -> setDefaultMaxXp(ctx.getSource(),
                                    IntegerArgumentType.getInteger(ctx, "xp"))))
                        )
                    )

                    // Player: version
                    .then(Commands.literal("version")
                        .executes(ctx -> showVersion(ctx.getSource()))
                    )

                    // Player: view own status
                    .then(Commands.literal("status")
                        .executes(ctx -> showPlayerStatus(ctx.getSource()))
                    )

                    // Player: view server defaults (read-only)
                    .then(Commands.literal("serverdefaults")
                        .executes(ctx -> showServerDefaults(ctx.getSource()))
                    )

                    // Admin: reload
                    .then(Commands.literal("reload")
                        .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .executes(ctx -> adminReload(ctx.getSource(), true))
                        .then(Commands.literal("broadcast")
                            .executes(ctx -> adminReload(ctx.getSource(), true)))
                        .then(Commands.literal("silent")
                            .executes(ctx -> adminReload(ctx.getSource(), false)))
                    )
            );
            dispatcher.register(Commands.literal("er")
                .redirect(dispatcher.getRoot().getChild("exprepair")));
        });
    }

    // =========================================================================
    // Player command handlers
    // =========================================================================

    private static int showHelp(CommandSourceStack source) {
        String version = getModVersion();
        source.sendSuccess(() -> header("expRepair v" + version, "Player Commands"), false);
        source.sendSuccess(() -> Component.empty(), false);

        source.sendSuccess(() -> Component.literal("  ")
            .append(suggestButton("/exprepair passive", "/exprepair passive", "Open passive repair options"))
            .append(Component.literal("\n    Auto-repairs all equipped Mending items once per second,\n"
                + "    spending up to " + maxXpPerRepair + " XP. Runs in the background.").withStyle(ChatFormatting.GRAY)), false);
        source.sendSuccess(() -> Component.literal("      ")
            .append(runButton("session", "/exprepair passive session",
                "Toggle passive repair for this session only.\nResets to permanent setting on disconnect."))
            .append(Component.literal("  "))
            .append(runButton("permanent", "/exprepair passive permanent",
                "Toggle passive repair permanently.\nPersists across restarts and logins.")), false);
        source.sendSuccess(() -> Component.empty(), false);

        source.sendSuccess(() -> Component.literal("  ")
            .append(suggestButton("/exprepair manual", "/exprepair manual", "Open manual repair options"))
            .append(Component.literal("\n    Sneak + right-click in air to repair the held Mending item.\n"
                + "    Uses up to " + maxXpPerRepair + " XP per click.").withStyle(ChatFormatting.GRAY)), false);
        source.sendSuccess(() -> Component.literal("      ")
            .append(runButton("session", "/exprepair manual session",
                "Toggle manual repair for this session only.\nResets to permanent setting on disconnect."))
            .append(Component.literal("  "))
            .append(runButton("permanent", "/exprepair manual permanent",
                "Toggle manual repair permanently.\nPersists across restarts and logins.")), false);
        source.sendSuccess(() -> Component.empty(), false);

        source.sendSuccess(() -> Component.literal("  ")
            .append(suggestButton("/exprepair threshold <levels>", "/exprepair threshold ",
                "Click to type: /exprepair threshold <levels>\nExample: /exprepair threshold 30"))
            .append(Component.literal("\n    Set an XP floor — passive repair will not spend XP that\n"
                + "    would drop you below this many levels. Use 0 to clear.").withStyle(ChatFormatting.GRAY)), false);
        source.sendSuccess(() -> Component.literal("      ")
            .append(runButton("view", "/exprepair threshold", "View your current XP threshold"))
            .append(Component.literal("  "))
            .append(suggestButton("set to...", "/exprepair threshold ",
                "Type a level number then Enter.\nExample: /exprepair threshold 30\nUse 0 to clear."))
            .append(Component.literal("  "))
            .append(runButton("clear", "/exprepair threshold 0",
                "Remove the XP floor — passive repair may use all XP.")), false);
        source.sendSuccess(() -> Component.empty(), false);

        source.sendSuccess(() -> Component.literal("  ")
            .append(runButton("/exprepair status", "/exprepair status",
                "View your current repair settings"))
            .append(Component.literal("\n    View your current passive, manual, and threshold settings.")
                .withStyle(ChatFormatting.GRAY)), false);
        source.sendSuccess(() -> Component.empty(), false);

        source.sendSuccess(() -> Component.literal("  ")
            .append(runButton("/exprepair serverdefaults", "/exprepair serverdefaults",
                "View server default settings"))
            .append(Component.literal("\n    View the server's default repair settings for new players.")
                .withStyle(ChatFormatting.GRAY)), false);

        if (source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            source.sendSuccess(() -> Component.empty(), false);
            source.sendSuccess(() -> header("expRepair", "Admin Commands"), false);
            source.sendSuccess(() -> Component.empty(), false);

            source.sendSuccess(() -> Component.literal("  ")
                .append(suggestButton("/exprepair admin <player>", "/exprepair admin ",
                    "Click to type: /exprepair admin <player>\nSub-commands: status, reset,\n  passive session|permanent on|off\n  manual session|permanent on|off\n  threshold <n>"))
                .append(Component.literal("\n    View or set a player's repair settings.\n"
                    + "    All changes can be session-only or permanent.").withStyle(ChatFormatting.GRAY)), false);
            source.sendSuccess(() -> Component.empty(), false);

            source.sendSuccess(() -> Component.literal("  ")
                .append(runButton("/exprepair default", "/exprepair default",
                    "View current server defaults"))
                .append(Component.literal("\n    View or change server-wide new-player defaults.\n"
                    + "    Saves to config/exprepair.json immediately.\n"
                    + "    Sub-commands: passive on|off, manual on|off,\n"
                    + "      threshold <n>, maxXpPerRepair <n>").withStyle(ChatFormatting.GRAY)), false);
            source.sendSuccess(() -> Component.empty(), false);

            source.sendSuccess(() -> Component.literal("  ")
                .append(runButton("/exprepair reload", "/exprepair reload",
                    "Re-read exprepair.json from disk.\nNo restart required."))
                .append(Component.literal("\n    Reload config/exprepair.json without restarting.\n"
                    + "    Updates all defaults immediately.").withStyle(ChatFormatting.GRAY)), false);
        }

        return 1;
    }

    private static int showOptions(CommandSourceStack source, String label, String subcommand,
                                   AttachmentType<Boolean> attachment, Map<UUID, Boolean> session)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        boolean featureAllowed = subcommand.equals("passive") ? allowPassive : allowManual;
        if (!featureAllowed) {
            source.sendSuccess(() -> header(label, "Options"), false);
            source.sendSuccess(() -> Component.literal("  This feature has been disabled by an administrator.")
                .withStyle(ChatFormatting.RED), false);
            return 1;
        }
        boolean permanent  = player.getAttachedOrElse(attachment, false);
        boolean hasSession = session.containsKey(player.getUUID());
        boolean effective  = hasSession ? session.get(player.getUUID()) : permanent;
        boolean newState   = !effective;
        String sourceLabel = hasSession ? " (session override)" : " (permanent)";

        source.sendSuccess(() -> header(label, "Options"), false);
        source.sendSuccess(() -> Component.empty(), false);

        source.sendSuccess(() -> Component.literal("  Status: ").withStyle(ChatFormatting.GRAY)
            .append(statusLabel(effective))
            .append(Component.literal(sourceLabel).withStyle(ChatFormatting.GRAY)), false);

        if (hasSession) {
            source.sendSuccess(() -> Component.literal("  Permanent setting: ").withStyle(ChatFormatting.GRAY)
                .append(statusLabel(permanent))
                .append(Component.literal(" (restores on next login)").withStyle(ChatFormatting.GRAY)), false);
        }

        source.sendSuccess(() -> Component.empty(), false);
        source.sendSuccess(() -> Component.literal("  Toggle ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal(newState ? "ON" : "OFF")
                .withStyle(newState ? ChatFormatting.GREEN : ChatFormatting.RED))
            .append(Component.literal(":").withStyle(ChatFormatting.GRAY)), false);

        source.sendSuccess(() -> Component.literal("    ")
            .append(runButton("this session only", "/exprepair " + subcommand + " session",
                "Toggle " + label + " for this session only.\n"
                + "Reverts to permanent setting on disconnect.\n"
                + "Command: /exprepair " + subcommand + " session"))
            .append(Component.literal("  — temporary, resets on disconnect.").withStyle(ChatFormatting.GRAY)), false);

        source.sendSuccess(() -> Component.literal("    ")
            .append(runButton("permanently", "/exprepair " + subcommand + " permanent",
                "Toggle " + label + " permanently.\n"
                + "Persists across logins and restarts.\n"
                + "Command: /exprepair " + subcommand + " permanent"))
            .append(Component.literal("  — survives disconnects and restarts.").withStyle(ChatFormatting.GRAY)), false);

        return 1;
    }

    private static int toggleFeature(CommandSourceStack source, String label, String subcommand,
                                     AttachmentType<Boolean> attachment, Map<UUID, Boolean> session,
                                     AttachmentType<Boolean> otherAttachment, Map<UUID, Boolean> otherSession,
                                     boolean sessionOnly) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        boolean featureAllowed = subcommand.equals("passive") ? allowPassive : allowManual;
        if (!featureAllowed) {
            source.sendFailure(Component.literal("  " + label + " has been disabled by the server administrator.")
                .withStyle(ChatFormatting.RED));
            return 0;
        }
        UUID uuid = player.getUUID();
        boolean permanent  = player.getAttachedOrElse(attachment, false);
        boolean hasSession = session.containsKey(uuid);
        boolean effective  = hasSession ? session.get(uuid) : permanent;
        boolean newState   = !effective;
        boolean otherWasOn = false;

        if (sessionOnly) {
            if (newState == permanent) { session.remove(uuid); } else { session.put(uuid, newState); }
            if (newState) {
                boolean otherPerm   = player.getAttachedOrElse(otherAttachment, false);
                boolean otherHasSes = otherSession.containsKey(uuid);
                otherWasOn = otherHasSes ? otherSession.get(uuid) : otherPerm;
                if (otherPerm) { otherSession.put(uuid, false); } else { otherSession.remove(uuid); }
            }
        } else {
            player.setAttached(attachment, newState);
            session.remove(uuid);
            if (newState) {
                boolean otherPerm   = player.getAttachedOrElse(otherAttachment, false);
                boolean otherHasSes = otherSession.containsKey(uuid);
                otherWasOn = otherHasSes ? otherSession.get(uuid) : otherPerm;
                player.setAttached(otherAttachment, false);
                otherSession.remove(uuid);
            }
        }

        final boolean disabledOther = newState && otherWasOn;
        final String  otherLabel    = subcommand.equals("passive") ? "Manual repair" : "Passive repair";
        final String  scope         = sessionOnly ? "for this session" : "permanently";

        source.sendSuccess(() -> Component.literal("  " + label + " ").withStyle(ChatFormatting.GRAY)
            .append(statusLabel(newState))
            .append(Component.literal("  (" + scope + ")").withStyle(ChatFormatting.GRAY)), false);

        if (disabledOther) {
            source.sendSuccess(() -> Component.literal("  " + otherLabel + " ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("OFF").withStyle(ChatFormatting.RED))
                .append(Component.literal("  (only one mode can be active at a time)").withStyle(ChatFormatting.GRAY)), false);
        }
        if (newState && subcommand.equals("manual")) {
            source.sendSuccess(() -> Component.literal("  Tip: sneak + right-click in air while holding a damaged Mending item.")
                .withStyle(ChatFormatting.GRAY), false);
        }
        if (newState && subcommand.equals("passive")) {
            source.sendSuccess(() -> Component.literal("  Tip: use ").withStyle(ChatFormatting.GRAY)
                .append(suggestButton("/exprepair threshold <levels>", "/exprepair threshold ",
                    "Protect a minimum XP level from passive repair.\nExample: /exprepair threshold 30"))
                .append(Component.literal(" to protect a minimum XP level.").withStyle(ChatFormatting.GRAY)), false);
        }
        source.sendSuccess(() -> Component.literal("  ")
            .append(runButton("undo", "/exprepair " + subcommand + (sessionOnly ? " session" : " permanent"),
                "Undo — turn " + label + " back " + (newState ? "OFF" : "ON") + " " + scope + ".")), false);

        return 1;
    }

    private static int showThreshold(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        int threshold = player.getAttachedOrElse(PASSIVE_THRESHOLD, 0);

        source.sendSuccess(() -> header("XP Threshold", "passive repair floor"), false);
        source.sendSuccess(() -> Component.empty(), false);
        source.sendSuccess(() -> Component.literal(
            "  Passive repair only spends XP above this level floor.\n"
            + "  If you drop to or below the threshold, passive repair\n"
            + "  pauses until you regain enough XP.").withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.empty(), false);

        if (threshold == 0) {
            source.sendSuccess(() -> Component.literal("  Current: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("none").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" — all XP may be used for repairs.").withStyle(ChatFormatting.GRAY)), false);
        } else {
            source.sendSuccess(() -> Component.literal("  Current: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(threshold + " levels").withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" — XP below this will not be spent.").withStyle(ChatFormatting.GRAY)), false);
        }

        source.sendSuccess(() -> Component.empty(), false);
        source.sendSuccess(() -> Component.literal("  ")
            .append(suggestButton("set threshold", "/exprepair threshold ",
                "Type a level number then Enter.\nExamples:\n  /exprepair threshold 30\n  /exprepair threshold 0  (clear)"))
            .append(Component.literal("  "))
            .append(runButton("clear", "/exprepair threshold 0",
                "Remove the XP floor — passive repair may use all XP.")), false);

        return 1;
    }

    private static int setThreshold(CommandSourceStack source, int levels) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        player.setAttached(PASSIVE_THRESHOLD, levels);

        if (levels == 0) {
            source.sendSuccess(() -> Component.literal("  XP threshold cleared. Passive repair may use all stored XP.")
                .withStyle(ChatFormatting.GREEN), false);
        } else {
            source.sendSuccess(() -> Component.literal("  XP threshold set to ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal(levels + " levels").withStyle(ChatFormatting.AQUA))
                .append(Component.literal(".").withStyle(ChatFormatting.GREEN)), false);
        }
        source.sendSuccess(() -> Component.literal("  ")
            .append(runButton("view threshold", "/exprepair threshold", "View your current XP threshold")), false);

        return 1;
    }

    // =========================================================================
    // Admin: per-player management
    // =========================================================================

    private static int adminStatus(CommandSourceStack source, ServerPlayer target) {
        boolean passivePerm     = target.getAttachedOrElse(PASSIVE_PERMANENT, defaultPassive);
        boolean manualPerm      = target.getAttachedOrElse(MANUAL_PERMANENT,  defaultManual);
        boolean passiveHasSes   = PASSIVE_SESSION.containsKey(target.getUUID());
        boolean manualHasSes    = MANUAL_SESSION.containsKey(target.getUUID());
        boolean passiveEffective = passiveHasSes ? PASSIVE_SESSION.get(target.getUUID()) : passivePerm;
        boolean manualEffective  = manualHasSes  ? MANUAL_SESSION.get(target.getUUID())  : manualPerm;
        int     threshold        = target.getAttachedOrElse(PASSIVE_THRESHOLD, defaultThreshold);
        String  name             = target.getName().getString();

        source.sendSuccess(() -> header(name, "expRepair Status"), false);
        source.sendSuccess(() -> Component.empty(), false);

        source.sendSuccess(() -> Component.literal("  Passive repair: ").withStyle(ChatFormatting.GRAY)
            .append(statusLabel(passiveEffective))
            .append(Component.literal(passiveHasSes ? "  (session — perm: " : "  (permanent)")
                .withStyle(ChatFormatting.GRAY))
            .append(passiveHasSes
                ? Component.literal(passivePerm ? "ON)" : "OFF)")
                    .withStyle(passivePerm ? ChatFormatting.GREEN : ChatFormatting.RED)
                : Component.literal("")), false);

        source.sendSuccess(() -> Component.literal("  Manual repair:  ").withStyle(ChatFormatting.GRAY)
            .append(statusLabel(manualEffective))
            .append(Component.literal(manualHasSes ? "  (session — perm: " : "  (permanent)")
                .withStyle(ChatFormatting.GRAY))
            .append(manualHasSes
                ? Component.literal(manualPerm ? "ON)" : "OFF)")
                    .withStyle(manualPerm ? ChatFormatting.GREEN : ChatFormatting.RED)
                : Component.literal("")), false);

        String threshStr = threshold == 0 ? "none" : threshold + " levels";
        source.sendSuccess(() -> Component.literal("  XP threshold:   ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal(threshStr)
                .withStyle(threshold == 0 ? ChatFormatting.YELLOW : ChatFormatting.AQUA)), false);

        source.sendSuccess(() -> Component.empty(), false);
        source.sendSuccess(() -> Component.literal("  Actions:  ").withStyle(ChatFormatting.GRAY)
            .append(runButton("reset " + name, "/exprepair admin " + name + " reset",
                "Reset " + name + " to server defaults:\n"
                + "  Passive: " + (defaultPassive ? "ON" : "OFF") + "\n"
                + "  Manual: "  + (defaultManual  ? "ON" : "OFF") + "\n"
                + "  Threshold: " + (defaultThreshold == 0 ? "none" : defaultThreshold + " levels") + "\n"
                + "Clears session overrides."))
            .append(Component.literal("  "))
            .append(suggestButton("passive...", "/exprepair admin " + name + " passive permanent ",
                "/exprepair admin " + name + " passive permanent on|off\n"
                + "/exprepair admin " + name + " passive session on|off"))
            .append(Component.literal("  "))
            .append(suggestButton("manual...", "/exprepair admin " + name + " manual permanent ",
                "/exprepair admin " + name + " manual permanent on|off\n"
                + "/exprepair admin " + name + " manual session on|off"))
            .append(Component.literal("  "))
            .append(suggestButton("threshold...", "/exprepair admin " + name + " threshold ",
                "/exprepair admin " + name + " threshold <levels>")), false);

        return 1;
    }

    private static int adminReset(CommandSourceStack source, ServerPlayer target) {
        target.setAttached(PASSIVE_PERMANENT, defaultPassive);
        target.setAttached(MANUAL_PERMANENT,  defaultManual);
        target.setAttached(PASSIVE_THRESHOLD, defaultThreshold);
        PASSIVE_SESSION.remove(target.getUUID());
        MANUAL_SESSION.remove(target.getUUID());

        String name      = target.getName().getString();
        String threshStr = defaultThreshold == 0 ? "none" : defaultThreshold + " levels";

        source.sendSuccess(() -> Component.literal("  " + name + " reset to server defaults.")
            .withStyle(ChatFormatting.GREEN), true);
        source.sendSuccess(() -> Component.literal("  Passive: ").withStyle(ChatFormatting.GRAY)
            .append(statusLabel(defaultPassive))
            .append(Component.literal("  Manual: ").withStyle(ChatFormatting.GRAY))
            .append(statusLabel(defaultManual))
            .append(Component.literal("  Threshold: ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(threshStr).withStyle(ChatFormatting.YELLOW)), false);

        target.displayClientMessage(header("expRepair", "your settings were reset by an admin"), false);
        target.displayClientMessage(
            Component.literal("  Passive: ").withStyle(ChatFormatting.GRAY)
                .append(statusLabel(defaultPassive))
                .append(Component.literal("  Manual: ").withStyle(ChatFormatting.GRAY))
                .append(statusLabel(defaultManual))
                .append(Component.literal("  Threshold: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(threshStr).withStyle(ChatFormatting.YELLOW)), false);
        target.displayClientMessage(
            Component.literal("  ").append(runButton("? help", "/exprepair", "View all expRepair commands")), false);

        return 1;
    }

    private static int adminSetPassive(CommandSourceStack source, ServerPlayer target,
                                       boolean sessionOnly, boolean state) {
        UUID   uuid = target.getUUID();
        String name = target.getName().getString();

        if (sessionOnly) {
            boolean perm = target.getAttachedOrElse(PASSIVE_PERMANENT, defaultPassive);
            if (state == perm) { PASSIVE_SESSION.remove(uuid); } else { PASSIVE_SESSION.put(uuid, state); }
            if (state) {
                boolean manPerm = target.getAttachedOrElse(MANUAL_PERMANENT, defaultManual);
                if (manPerm) { MANUAL_SESSION.put(uuid, false); } else { MANUAL_SESSION.remove(uuid); }
            }
        } else {
            target.setAttached(PASSIVE_PERMANENT, state);
            PASSIVE_SESSION.remove(uuid);
            if (state) { target.setAttached(MANUAL_PERMANENT, false); MANUAL_SESSION.remove(uuid); }
        }

        final String scope = sessionOnly ? "this session" : "permanently";
        source.sendSuccess(() -> Component.literal("  " + name + " passive repair ").withStyle(ChatFormatting.GRAY)
            .append(statusLabel(state))
            .append(Component.literal("  (" + scope + ")").withStyle(ChatFormatting.GRAY)), true);
        if (state) {
            source.sendSuccess(() -> Component.literal("  " + name + " manual repair ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("OFF").withStyle(ChatFormatting.RED))
                .append(Component.literal("  (only one mode active at a time)").withStyle(ChatFormatting.GRAY)), false);
        }

        target.displayClientMessage(
            Component.literal("  expRepair ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal("Passive repair ").withStyle(s -> s.withColor(ChatFormatting.GRAY).withBold(false)))
                .append(statusLabel(state))
                .append(Component.literal(" set by admin (" + scope + ").").withStyle(ChatFormatting.GRAY)), false);

        return 1;
    }

    private static int adminSetManual(CommandSourceStack source, ServerPlayer target,
                                      boolean sessionOnly, boolean state) {
        UUID   uuid = target.getUUID();
        String name = target.getName().getString();

        if (sessionOnly) {
            boolean perm = target.getAttachedOrElse(MANUAL_PERMANENT, defaultManual);
            if (state == perm) { MANUAL_SESSION.remove(uuid); } else { MANUAL_SESSION.put(uuid, state); }
            if (state) {
                boolean passPerm = target.getAttachedOrElse(PASSIVE_PERMANENT, defaultPassive);
                if (passPerm) { PASSIVE_SESSION.put(uuid, false); } else { PASSIVE_SESSION.remove(uuid); }
            }
        } else {
            target.setAttached(MANUAL_PERMANENT, state);
            MANUAL_SESSION.remove(uuid);
            if (state) { target.setAttached(PASSIVE_PERMANENT, false); PASSIVE_SESSION.remove(uuid); }
        }

        final String scope = sessionOnly ? "this session" : "permanently";
        source.sendSuccess(() -> Component.literal("  " + name + " manual repair ").withStyle(ChatFormatting.GRAY)
            .append(statusLabel(state))
            .append(Component.literal("  (" + scope + ")").withStyle(ChatFormatting.GRAY)), true);
        if (state) {
            source.sendSuccess(() -> Component.literal("  " + name + " passive repair ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("OFF").withStyle(ChatFormatting.RED))
                .append(Component.literal("  (only one mode active at a time)").withStyle(ChatFormatting.GRAY)), false);
        }

        target.displayClientMessage(
            Component.literal("  expRepair ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal("Manual repair ").withStyle(s -> s.withColor(ChatFormatting.GRAY).withBold(false)))
                .append(statusLabel(state))
                .append(Component.literal(" set by admin (" + scope + ").").withStyle(ChatFormatting.GRAY)), false);
        if (state) {
            target.displayClientMessage(
                Component.literal("  Sneak + right-click in air to repair a held Mending item.")
                    .withStyle(ChatFormatting.GRAY), false);
        }

        return 1;
    }

    private static int adminSetThreshold(CommandSourceStack source, ServerPlayer target, int levels) {
        target.setAttached(PASSIVE_THRESHOLD, levels);
        String name      = target.getName().getString();
        String threshStr = levels == 0 ? "none" : levels + " levels";

        source.sendSuccess(() -> Component.literal("  " + name + " XP threshold set to ").withStyle(ChatFormatting.GREEN)
            .append(Component.literal(threshStr).withStyle(levels == 0 ? ChatFormatting.YELLOW : ChatFormatting.AQUA))
            .append(Component.literal(".").withStyle(ChatFormatting.GREEN)), true);

        target.displayClientMessage(
            Component.literal("  expRepair ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal("XP threshold set to ").withStyle(s -> s.withColor(ChatFormatting.GRAY).withBold(false)))
                .append(Component.literal(threshStr).withStyle(levels == 0 ? ChatFormatting.YELLOW : ChatFormatting.AQUA))
                .append(Component.literal(" by admin.").withStyle(ChatFormatting.GRAY)), false);

        return 1;
    }

    private static int adminReload(CommandSourceStack source, boolean doBroadcast) {
        loadConfig();
        source.sendSuccess(() -> header("expRepair", "config reloaded"), false);
        source.sendSuccess(() -> Component.empty(), false);
        source.sendSuccess(() -> Component.literal("  maxXpPerRepair:    ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal(maxXpPerRepair + " XP").withStyle(ChatFormatting.AQUA)), false);
        source.sendSuccess(() -> Component.literal("  Default passive:   ").withStyle(ChatFormatting.GRAY)
            .append(statusLabel(defaultPassive)), false);
        source.sendSuccess(() -> Component.literal("  Allow passive:     ").withStyle(ChatFormatting.GRAY)
            .append(statusLabel(allowPassive)), false);
        source.sendSuccess(() -> Component.literal("  Default manual:    ").withStyle(ChatFormatting.GRAY)
            .append(statusLabel(defaultManual)), false);
        source.sendSuccess(() -> Component.literal("  Allow manual:      ").withStyle(ChatFormatting.GRAY)
            .append(statusLabel(allowManual)), false);
        source.sendSuccess(() -> Component.literal("  Default threshold: ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal(defaultThreshold == 0 ? "none" : defaultThreshold + " levels")
                .withStyle(defaultThreshold == 0 ? ChatFormatting.YELLOW : ChatFormatting.AQUA)), false);
        if (doBroadcast) {
            String threshStr = defaultThreshold == 0 ? "none" : defaultThreshold + " levels";
            MutableComponent broadcast = header("expRepair", "config reloaded")
                .append(Component.literal("\n  Max XP/repair: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(maxXpPerRepair + " XP").withStyle(ChatFormatting.AQUA))
                .append(Component.literal("  |  Passive: ").withStyle(ChatFormatting.GRAY))
                .append(allowPassive ? statusLabel(defaultPassive) : Component.literal("DISABLED").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("  |  Manual: ").withStyle(ChatFormatting.GRAY))
                .append(allowManual ? statusLabel(defaultManual) : Component.literal("DISABLED").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("  |  Threshold: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(threshStr).withStyle(defaultThreshold == 0 ? ChatFormatting.YELLOW : ChatFormatting.AQUA));
            source.getServer().getPlayerList().getPlayers()
                .forEach(p -> p.displayClientMessage(broadcast, false));
        }
        return 1;
    }

    private static String getModVersion() {
        return FabricLoader.getInstance().getModContainer("exprepair")
            .map(c -> c.getMetadata().getVersion().getFriendlyString())
            .orElse("unknown");
    }

    private static int showVersion(CommandSourceStack source) {
        String version = getModVersion();
        source.sendSuccess(() -> Component.literal("◆ ").withStyle(ChatFormatting.GOLD)
            .append(Component.literal("expRepair").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
            .append(Component.literal(" v" + version).withStyle(s -> s.withColor(ChatFormatting.AQUA).withBold(false))), false);
        return 1;
    }

    private static int showPlayerStatus(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        boolean passivePerm    = player.getAttachedOrElse(PASSIVE_PERMANENT, defaultPassive);
        boolean manualPerm     = player.getAttachedOrElse(MANUAL_PERMANENT,  defaultManual);
        boolean passiveHasSes  = PASSIVE_SESSION.containsKey(player.getUUID());
        boolean manualHasSes   = MANUAL_SESSION.containsKey(player.getUUID());
        boolean passiveEff     = passiveHasSes ? PASSIVE_SESSION.get(player.getUUID()) : passivePerm;
        boolean manualEff      = manualHasSes  ? MANUAL_SESSION.get(player.getUUID())  : manualPerm;
        int     threshold      = player.getAttachedOrElse(PASSIVE_THRESHOLD, defaultThreshold);

        source.sendSuccess(() -> header("expRepair", "Your Settings"), false);
        source.sendSuccess(() -> Component.empty(), false);

        MutableComponent passiveLine = Component.literal("  Passive repair: ").withStyle(ChatFormatting.GRAY);
        if (!allowPassive) {
            passiveLine.append(Component.literal("DISABLED").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(" (blocked by admin)").withStyle(ChatFormatting.GRAY));
        } else {
            passiveLine.append(statusLabel(passiveEff))
                .append(Component.literal(passiveHasSes ? "  (session — perm: " : "  (permanent)")
                    .withStyle(ChatFormatting.GRAY));
            if (passiveHasSes) {
                passiveLine.append(Component.literal(passivePerm ? "ON)" : "OFF)")
                    .withStyle(passivePerm ? ChatFormatting.GREEN : ChatFormatting.RED));
            }
        }
        source.sendSuccess(() -> passiveLine, false);

        MutableComponent manualLine = Component.literal("  Manual repair:  ").withStyle(ChatFormatting.GRAY);
        if (!allowManual) {
            manualLine.append(Component.literal("DISABLED").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(" (blocked by admin)").withStyle(ChatFormatting.GRAY));
        } else {
            manualLine.append(statusLabel(manualEff))
                .append(Component.literal(manualHasSes ? "  (session — perm: " : "  (permanent)")
                    .withStyle(ChatFormatting.GRAY));
            if (manualHasSes) {
                manualLine.append(Component.literal(manualPerm ? "ON)" : "OFF)")
                    .withStyle(manualPerm ? ChatFormatting.GREEN : ChatFormatting.RED));
            }
        }
        source.sendSuccess(() -> manualLine, false);

        String threshStr = threshold == 0 ? "none" : threshold + " levels";
        source.sendSuccess(() -> Component.literal("  XP threshold:   ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal(threshStr)
                .withStyle(threshold == 0 ? ChatFormatting.YELLOW : ChatFormatting.AQUA)), false);

        source.sendSuccess(() -> Component.empty(), false);
        source.sendSuccess(() -> Component.literal("  ")
            .append(runButton("configure passive", "/exprepair passive", "Open passive repair options"))
            .append(Component.literal("  "))
            .append(runButton("configure manual", "/exprepair manual", "Open manual repair options"))
            .append(Component.literal("  "))
            .append(suggestButton("set threshold", "/exprepair threshold ",
                "Set your XP floor\n/exprepair threshold <levels>")), false);

        return 1;
    }

    private static int showServerDefaults(CommandSourceStack source) {
        source.sendSuccess(() -> header("expRepair", "Server Defaults"), false);
        source.sendSuccess(() -> Component.empty(), false);
        source.sendSuccess(() -> Component.literal(
            "  These are the server's default settings for new players.").withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.empty(), false);

        source.sendSuccess(() -> Component.literal("  Passive repair:    ").withStyle(ChatFormatting.GRAY)
            .append(allowPassive ? statusLabel(defaultPassive)
                : Component.literal("DISABLED").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(" (blocked by admin)").withStyle(ChatFormatting.GRAY))), false);

        source.sendSuccess(() -> Component.literal("  Manual repair:     ").withStyle(ChatFormatting.GRAY)
            .append(allowManual ? statusLabel(defaultManual)
                : Component.literal("DISABLED").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(" (blocked by admin)").withStyle(ChatFormatting.GRAY))), false);

        String threshStr = defaultThreshold == 0 ? "none" : defaultThreshold + " levels";
        source.sendSuccess(() -> Component.literal("  Default threshold: ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal(threshStr)
                .withStyle(defaultThreshold == 0 ? ChatFormatting.YELLOW : ChatFormatting.AQUA)), false);

        source.sendSuccess(() -> Component.literal("  Max XP per repair: ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal(maxXpPerRepair + " XP").withStyle(ChatFormatting.AQUA)), false);

        return 1;
    }

    // =========================================================================
    // Admin: server defaults
    // =========================================================================

    private static int showDefaults(CommandSourceStack source) {
        source.sendSuccess(() -> header("expRepair", "Server Defaults"), false);
        source.sendSuccess(() -> Component.empty(), false);
        source.sendSuccess(() -> Component.literal(
            "  Applied to new players with no saved data.\n"
            + "  Stored in config/exprepair.json.").withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.empty(), false);

        source.sendSuccess(() -> Component.literal("  Passive repair:    ").withStyle(ChatFormatting.GRAY)
            .append(statusLabel(defaultPassive)).append(Component.literal("  "))
            .append(runButton("on",  "/exprepair default passive on",  "Set default passive repair ON"))
            .append(Component.literal("  "))
            .append(runButton("off", "/exprepair default passive off", "Set default passive repair OFF")), false);

        source.sendSuccess(() -> Component.literal("  Passive allowed:   ").withStyle(ChatFormatting.GRAY)
            .append(statusLabel(allowPassive)).append(Component.literal("  "))
            .append(runButton("allow", "/exprepair default passive allow on",
                "Allow players to enable passive repair"))
            .append(Component.literal("  "))
            .append(runButton("block", "/exprepair default passive allow off",
                "Prevent players from using passive repair entirely")), false);

        source.sendSuccess(() -> Component.literal("  Manual repair:     ").withStyle(ChatFormatting.GRAY)
            .append(statusLabel(defaultManual)).append(Component.literal("  "))
            .append(runButton("on",  "/exprepair default manual on",  "Set default manual repair ON"))
            .append(Component.literal("  "))
            .append(runButton("off", "/exprepair default manual off", "Set default manual repair OFF")), false);

        source.sendSuccess(() -> Component.literal("  Manual allowed:    ").withStyle(ChatFormatting.GRAY)
            .append(statusLabel(allowManual)).append(Component.literal("  "))
            .append(runButton("allow", "/exprepair default manual allow on",
                "Allow players to enable manual repair"))
            .append(Component.literal("  "))
            .append(runButton("block", "/exprepair default manual allow off",
                "Prevent players from using manual repair entirely")), false);

        String threshStr = defaultThreshold == 0 ? "none" : defaultThreshold + " levels";
        source.sendSuccess(() -> Component.literal("  Default threshold: ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal(threshStr)
                .withStyle(defaultThreshold == 0 ? ChatFormatting.YELLOW : ChatFormatting.AQUA))
            .append(Component.literal("  "))
            .append(suggestButton("set", "/exprepair default threshold ",
                "Set default XP threshold\n/exprepair default threshold <levels>"))
            .append(Component.literal("  "))
            .append(runButton("clear", "/exprepair default threshold 0", "Set default threshold to 0")), false);

        source.sendSuccess(() -> Component.literal("  Max XP per repair: ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal(maxXpPerRepair + " XP").withStyle(ChatFormatting.AQUA))
            .append(Component.literal("  "))
            .append(suggestButton("set", "/exprepair default maxXpPerRepair ",
                "Set max XP spent per repair tick\n/exprepair default maxXpPerRepair <xp>")), false);

        return 1;
    }

    private static int setDefaultPassive(CommandSourceStack source, boolean state) {
        defaultPassive = state;
        saveConfig();
        source.sendSuccess(() -> Component.literal("  Default passive repair set to ").withStyle(ChatFormatting.GREEN)
            .append(statusLabel(state))
            .append(Component.literal(". Saved to config.").withStyle(ChatFormatting.GREEN)), true);
        source.sendSuccess(() -> Component.literal("  Use ").withStyle(ChatFormatting.GRAY)
            .append(suggestButton("/exprepair admin <player> reset", "/exprepair admin ",
                "Reset a specific player to apply new defaults"))
            .append(Component.literal(" to apply to an existing player.").withStyle(ChatFormatting.GRAY)), false);
        return 1;
    }

    private static int setDefaultManual(CommandSourceStack source, boolean state) {
        defaultManual = state;
        saveConfig();
        source.sendSuccess(() -> Component.literal("  Default manual repair set to ").withStyle(ChatFormatting.GREEN)
            .append(statusLabel(state))
            .append(Component.literal(". Saved to config.").withStyle(ChatFormatting.GREEN)), true);
        source.sendSuccess(() -> Component.literal("  Use ").withStyle(ChatFormatting.GRAY)
            .append(suggestButton("/exprepair admin <player> reset", "/exprepair admin ",
                "Reset a specific player to apply new defaults"))
            .append(Component.literal(" to apply to an existing player.").withStyle(ChatFormatting.GRAY)), false);
        return 1;
    }

    private static int setAllowPassive(CommandSourceStack source, boolean state, boolean doBroadcast) {
        allowPassive = state;
        saveConfig();
        if (state) {
            source.sendSuccess(() -> Component.literal("  Passive repair is now ")
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal("ALLOWED").withStyle(ChatFormatting.GREEN))
                .append(Component.literal(". Players may enable it. Saved to config.").withStyle(ChatFormatting.GREEN)), true);
        } else {
            source.sendSuccess(() -> Component.literal("  Passive repair is now ")
                .withStyle(ChatFormatting.RED)
                .append(Component.literal("BLOCKED").withStyle(ChatFormatting.RED))
                .append(Component.literal(". No player can use passive repair. Saved to config.").withStyle(ChatFormatting.RED)), true);
        }
        if (doBroadcast) {
            MutableComponent broadcast = header("expRepair", "server update")
                .append(Component.literal("\n  Passive repair has been ").withStyle(ChatFormatting.GRAY))
                .append(state
                    ? Component.literal("ALLOWED").withStyle(ChatFormatting.GREEN)
                    : Component.literal("BLOCKED").withStyle(ChatFormatting.RED))
                .append(Component.literal(" by an administrator.").withStyle(ChatFormatting.GRAY));
            source.getServer().getPlayerList().getPlayers()
                .forEach(p -> p.displayClientMessage(broadcast, false));
        }
        return 1;
    }

    private static int setAllowManual(CommandSourceStack source, boolean state, boolean doBroadcast) {
        allowManual = state;
        saveConfig();
        if (state) {
            source.sendSuccess(() -> Component.literal("  Manual repair is now ")
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal("ALLOWED").withStyle(ChatFormatting.GREEN))
                .append(Component.literal(". Players may enable it. Saved to config.").withStyle(ChatFormatting.GREEN)), true);
        } else {
            source.sendSuccess(() -> Component.literal("  Manual repair is now ")
                .withStyle(ChatFormatting.RED)
                .append(Component.literal("BLOCKED").withStyle(ChatFormatting.RED))
                .append(Component.literal(". No player can use manual repair. Saved to config.").withStyle(ChatFormatting.RED)), true);
        }
        if (doBroadcast) {
            MutableComponent broadcast = header("expRepair", "server update")
                .append(Component.literal("\n  Manual repair has been ").withStyle(ChatFormatting.GRAY))
                .append(state
                    ? Component.literal("ALLOWED").withStyle(ChatFormatting.GREEN)
                    : Component.literal("BLOCKED").withStyle(ChatFormatting.RED))
                .append(Component.literal(" by an administrator.").withStyle(ChatFormatting.GRAY));
            source.getServer().getPlayerList().getPlayers()
                .forEach(p -> p.displayClientMessage(broadcast, false));
        }
        return 1;
    }

    private static int setDefaultThreshold(CommandSourceStack source, int levels) {
        defaultThreshold = levels;
        saveConfig();
        String threshStr = levels == 0 ? "none" : levels + " levels";
        source.sendSuccess(() -> Component.literal("  Default XP threshold set to ").withStyle(ChatFormatting.GREEN)
            .append(Component.literal(threshStr).withStyle(levels == 0 ? ChatFormatting.YELLOW : ChatFormatting.AQUA))
            .append(Component.literal(". Saved to config.").withStyle(ChatFormatting.GREEN)), true);
        return 1;
    }

    private static int setDefaultMaxXp(CommandSourceStack source, int xp) {
        maxXpPerRepair = xp;
        saveConfig();
        source.sendSuccess(() -> Component.literal("  Max XP per repair set to ").withStyle(ChatFormatting.GREEN)
            .append(Component.literal(xp + " XP").withStyle(ChatFormatting.AQUA))
            .append(Component.literal(". Saved to config.").withStyle(ChatFormatting.GREEN)), true);
        return 1;
    }

    // =========================================================================
    // Config
    // =========================================================================

    private static void loadConfig() {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("exprepair.json");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        if (Files.exists(configPath)) {
            try (Reader reader = Files.newBufferedReader(configPath)) {
                JsonObject obj = gson.fromJson(reader, JsonObject.class);
                if (obj != null) {
                    if (obj.has("maxXpPerRepair"))  maxXpPerRepair   = Math.max(1,  obj.get("maxXpPerRepair").getAsInt());
                    if (obj.has("defaultPassive"))   defaultPassive   = obj.get("defaultPassive").getAsBoolean();
                    if (obj.has("defaultManual"))    defaultManual    = obj.get("defaultManual").getAsBoolean();
                    if (obj.has("defaultThreshold")) defaultThreshold = Math.max(0, obj.get("defaultThreshold").getAsInt());
                    if (obj.has("allowPassive"))     allowPassive     = obj.get("allowPassive").getAsBoolean();
                    if (obj.has("allowManual"))      allowManual      = obj.get("allowManual").getAsBoolean();
                }
            } catch (Exception e) { /* keep defaults */ }
        } else {
            saveConfig();
        }
    }

    private static void saveConfig() {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("exprepair.json");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        JsonObject obj = new JsonObject();
        obj.addProperty("maxXpPerRepair",   maxXpPerRepair);
        obj.addProperty("defaultPassive",   defaultPassive);
        obj.addProperty("defaultManual",    defaultManual);
        obj.addProperty("defaultThreshold", defaultThreshold);
        obj.addProperty("allowPassive",     allowPassive);
        obj.addProperty("allowManual",      allowManual);
        try (Writer writer = Files.newBufferedWriter(configPath)) {
            gson.toJson(obj, writer);
        } catch (Exception e) { /* ignore */ }
    }

    // =========================================================================
    // UI helpers
    // =========================================================================

    private static MutableComponent header(String title, String subtitle) {
        return Component.literal("◆ ").withStyle(ChatFormatting.GOLD)
            .append(Component.literal(title).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
            .append(Component.literal(" — " + subtitle)
                .withStyle(s -> s.withColor(ChatFormatting.GRAY).withBold(false)));
    }

    private static MutableComponent statusLabel(boolean on) {
        return on
            ? Component.literal("ON").withStyle(ChatFormatting.GREEN)
            : Component.literal("OFF").withStyle(ChatFormatting.RED);
    }

    private static MutableComponent suggestButton(String label, String command, String tooltip) {
        return Component.literal("[" + label + "]")
            .withStyle(style -> style
                .withColor(ChatFormatting.AQUA)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent.SuggestCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(tooltip)))
            );
    }

    private static MutableComponent runButton(String label, String command, String tooltip) {
        return Component.literal("[" + label + "]")
            .withStyle(style -> style
                .withColor(ChatFormatting.GREEN)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(tooltip)))
            );
    }

    // =========================================================================
    // Shared helpers
    // =========================================================================

    private static boolean isEligible(ServerPlayer player) {
        return player.isAlive()
            && player.gameMode.getGameModeForPlayer() == GameType.SURVIVAL;
    }

    private static boolean isEffective(Player player, AttachmentType<Boolean> attachment,
                                       Map<UUID, Boolean> session) {
        boolean permanent = player.getAttachedOrElse(attachment, false);
        return session.getOrDefault(player.getUUID(), permanent);
    }

    private static int getTotalXp(Player player) {
        return getXpToReachLevel(player.experienceLevel)
            + Math.round(player.experienceProgress * player.getXpNeededForNextLevel());
    }

    private static void drainXp(Player player, int amount) {
        int remaining = Math.max(0, getTotalXp(player) - amount);
        int newLevel  = getLevelFromXp(remaining);
        int leftover  = remaining - getXpToReachLevel(newLevel);
        ServerPlayer serverPlayer = (ServerPlayer) player;
        serverPlayer.setExperienceLevels(newLevel);
        serverPlayer.setExperiencePoints(leftover);
    }

    private static int getLevelFromXp(int totalXp) {
        int level = 0;
        while (getXpToReachLevel(level + 1) <= totalXp) level++;
        return level;
    }

    private static int getXpToReachLevel(int level) {
        if (level <= 16) return level * level + 6 * level;
        if (level <= 31) return (int)(2.5 * level * level - 40.5 * level + 360);
        return (int)(4.5 * level * level - 162.5 * level + 2220);
    }

    private static int getXpForNextLevel(int level) {
        if (level < 16) return 2 * level + 7;
        if (level < 31) return 5 * level - 38;
        return 9 * level - 158;
    }
}
